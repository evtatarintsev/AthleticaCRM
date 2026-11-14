package org.athletica.crm.read.groups

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.Row
import kotlinx.datetime.LocalDate
import org.athletica.crm.api.schemas.groups.GroupClientAttention
import org.athletica.crm.api.schemas.groups.GroupRosterClient
import org.athletica.crm.api.schemas.groups.GroupRosterMembership
import org.athletica.crm.api.schemas.groups.GroupRosterResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.entityids.toMembershipId
import org.athletica.crm.core.entityids.toUploadId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.subscription.ActiveStatus
import org.athletica.crm.core.subscription.Remaining
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asInt
import org.athletica.crm.storage.asIntOrNull
import org.athletica.crm.storage.asLocalDate
import org.athletica.crm.storage.asLocalDateOrNull
import org.athletica.crm.storage.asMoney
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull

/**
 * Реализация [GroupRosterView]: сегодняшняя дата организации на момент запроса с проверкой
 * группы, затем один SQL по участникам с выбранным по ступеням абонементом группы, числом
 * неоплаченных занятий и балансом. Признаки «заканчивается» и «к закрытию» выводит общий
 * [ActiveStatus.of], причину внимания и порядок по ней — Kotlin после SQL: состав одной группы
 * невелик и не постраничный.
 */
class DbGroupRosterView : GroupRosterView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun list(query: GroupRosterQuery): GroupRosterResponse {
        val today =
            tr
                .sql(
                    """
                    SELECT (CAST(:now AS timestamptz) AT TIME ZONE o.timezone)::date AS today
                      FROM groups g
                      JOIN organizations o ON o.id = g.org_id
                     WHERE g.id = :groupId AND g.org_id = :orgId AND g.branch_id = :branchId
                    """.trimIndent(),
                )
                .bind("now", query.now)
                .bind("groupId", query.groupId)
                .bind("orgId", ctx.orgId)
                .bind("branchId", ctx.branchId)
                .firstOrNull { it.asLocalDate("today") }
                ?: raise(CommonDomainError("GROUP_NOT_FOUND", Messages.GroupNotFound.localize()))
        val clients =
            tr
                .sql(SQL)
                .bind("groupId", query.groupId)
                .bind("orgId", ctx.orgId)
                .bind("today", today)
                .list { it.toClient(today) }
        return GroupRosterResponse(clients.sortedWith(compareBy(nullsLast()) { it.attention }))
    }

    /** Участник из строки выборки с признаками абонемента на дату [today]. */
    context(ctx: EmployeeRequestContext)
    private fun Row.toClient(today: LocalDate): GroupRosterClient {
        val status = activeStatus(today)
        val membership = toMembership(status)
        val hasNext = asBoolean("has_next")
        val unpaid = asInt("unpaid_sessions")
        val balance = asMoney("balance", ctx.currency)
        val attention =
            when {
                unpaid > 0 || balance.isNegative -> GroupClientAttention.DEBT
                membership == null || membership.closePending -> GroupClientAttention.NO_MEMBERSHIP
                membership.endingSoon.isNotEmpty() && !hasNext -> GroupClientAttention.MEMBERSHIP_ENDING
                else -> null
            }
        return GroupRosterClient(
            id = asUuid("client_id").toClientId(),
            name = asString("client_name"),
            avatarId = asUuidOrNull("avatar_id")?.toUploadId(),
            membership = membership,
            hasNextMembership = hasNext,
            unpaidSessions = unpaid,
            canRepay = unpaid > 0 && status != null && status.remaining != Remaining.Exhausted,
            balance = balance,
            attention = attention,
        )
    }

    /** Признаки активного абонемента на дату [today]; `null` — абонемента нет или он не активирован. */
    private fun Row.activeStatus(today: LocalDate): ActiveStatus? {
        val start = asLocalDateOrNull("start_date")
        val end = asLocalDateOrNull("end_date")
        if (start == null || end == null || asString("state") != "ACTIVE") {
            return null
        }
        return ActiveStatus.of(start, end, asIntOrNull("sessions_remaining"), today)
    }

    /** Абонемент, действующий в группе, с признаками [status]; `null` — его нет. */
    private fun Row.toMembership(status: ActiveStatus?): GroupRosterMembership? {
        val id = asUuidOrNull("membership_id") ?: return null
        return GroupRosterMembership(
            id = id.toMembershipId(),
            name = asString("membership_name"),
            activated = status != null,
            sessionsTotal = asIntOrNull("sessions_total"),
            sessionsRemaining = asIntOrNull("sessions_remaining"),
            endDate = asLocalDateOrNull("end_date"),
            endingSoon = status?.endingSoon?.sorted().orEmpty(),
            closePending = status?.closePending ?: false,
        )
    }

    private companion object {
        /**
         * Активные участники группы. Ступень абонемента «к закрытию» — то же определение, что
         * [ActiveStatus.closePending]: срок вышел или остаток 0; нужна SQL-копия, чтобы выбрать
         * абонемент в `LATERAL`.
         */
        val SQL =
            """
            SELECT
                c.id AS client_id,
                c.name AS client_name,
                c.avatar_id,
                m.id AS membership_id,
                m.name AS membership_name,
                m.state,
                m.sessions_total,
                m.sessions_remaining,
                m.start_date,
                m.end_date,
                EXISTS (SELECT 1
                          FROM memberships n
                          JOIN membership_groups ng ON ng.membership_id = n.id AND ng.group_id = e.group_id
                         WHERE n.client_id = c.id AND n.org_id = c.org_id AND n.state = 'INACTIVE'
                           AND n.id IS DISTINCT FROM m.id) AS has_next,
                (SELECT COUNT(*)::int
                   FROM session_attendance a
                   JOIN sessions s ON s.id = a.session_id
                  WHERE a.client_id = c.id AND a.presence = 'present'
                    AND s.group_id = e.group_id AND s.org_id = c.org_id AND s.status = 'completed'
                    AND NOT EXISTS (SELECT 1 FROM membership_debits d
                                     WHERE d.session_id = s.id AND d.client_id = c.id
                                       AND d.reversed_at IS NULL)) AS unpaid_sessions,
                COALESCE((SELECT j.balance_after
                            FROM client_balance_journal j
                           WHERE j.client_id = c.id AND j.org_id = c.org_id
                           ORDER BY j.created_at DESC
                           LIMIT 1), 0) AS balance
            FROM enrollments e
            JOIN clients c ON c.id = e.client_id AND c.org_id = :orgId
            LEFT JOIN LATERAL (
                SELECT mm.*
                  FROM memberships mm
                  JOIN membership_groups mg ON mg.membership_id = mm.id AND mg.group_id = e.group_id
                 WHERE mm.client_id = c.id AND mm.org_id = c.org_id AND mm.state IN ('ACTIVE', 'INACTIVE')
                 ORDER BY CASE
                              WHEN mm.state = 'INACTIVE' THEN 1
                              WHEN mm.end_date < :today OR mm.sessions_remaining <= 0 THEN 2
                              ELSE 0
                          END,
                          mm.end_date NULLS LAST, mm.issued_on, mm.created_at, mm.id
                 LIMIT 1
            ) m ON TRUE
            WHERE e.group_id = :groupId AND e.left_at IS NULL
            ORDER BY c.name, c.id
            """.trimIndent()
    }
}
