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
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.entityids.toMembershipId
import org.athletica.crm.core.entityids.toUploadId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.subscription.ActiveStatus
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asInt
import org.athletica.crm.storage.asIntOrNull
import org.athletica.crm.storage.asLocalDate
import org.athletica.crm.storage.asLocalDateOrNull
import org.athletica.crm.storage.asMoney
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuidOrNull

/**
 * Реализация [GroupRosterView] одним SQL: строка на активного участника с выбранным по ступеням
 * абонементом группы, числом неоплаченных занятий и балансом. Признаки «заканчивается» и
 * «к закрытию» выводит общий [ActiveStatus.of] на сегодняшнюю дату организации, причину внимания
 * и порядок по ней — Kotlin после SQL: состав одной группы невелик и не постраничный.
 */
class DbGroupRosterView : GroupRosterView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun list(groupId: GroupId): GroupRosterResponse {
        val rows =
            tr
                .sql(SQL)
                .bind("groupId", groupId)
                .bind("orgId", ctx.orgId)
                .bind("branchId", ctx.branchId)
                .list { row -> listOfNotNull(row.toClient()) }
        if (rows.isEmpty()) {
            raise(CommonDomainError("GROUP_NOT_FOUND", Messages.GroupNotFound.localize()))
        }
        val clients = rows.flatten().sortedBy { it.attention?.ordinal ?: GroupClientAttention.entries.size }
        return GroupRosterResponse(clients)
    }

    /** Участник из строки выборки; `null` — строка пустой группы без участников. */
    context(ctx: EmployeeRequestContext)
    private fun Row.toClient(): GroupRosterClient? {
        val id = asUuidOrNull("client_id") ?: return null
        val membership = toMembership(asLocalDate("today"))
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
            id = id.toClientId(),
            name = asString("client_name"),
            avatarId = asUuidOrNull("avatar_id")?.toUploadId(),
            membership = membership,
            hasNextMembership = hasNext,
            unpaidSessions = unpaid,
            balance = balance,
            attention = attention,
        )
    }

    /** Абонемент, действующий в группе, с признаками на дату [today]; `null` — его нет. */
    private fun Row.toMembership(today: LocalDate): GroupRosterMembership? {
        val id = asUuidOrNull("membership_id") ?: return null
        val remaining = asIntOrNull("sessions_remaining")
        val start = asLocalDateOrNull("start_date")
        val end = asLocalDateOrNull("end_date")
        val status =
            if (asString("state") == "ACTIVE" && start != null && end != null) {
                ActiveStatus.of(start, end, remaining, today)
            } else {
                null
            }
        return GroupRosterMembership(
            id = id.toMembershipId(),
            name = asString("membership_name"),
            activated = status != null,
            sessionsTotal = asIntOrNull("sessions_total"),
            sessionsRemaining = remaining,
            endDate = end,
            endingSoon = status?.endingSoon?.sorted().orEmpty(),
            closePending = status?.closePending ?: false,
        )
    }

    private companion object {
        /**
         * Строка группы с участниками: пустая группа даёт одну строку без участника, чужая —
         * ни одной. Ступень абонемента «к закрытию» — то же определение, что [ActiveStatus.closePending]:
         * срок вышел или остаток 0.
         */
        val SQL =
            """
            WITH grp AS (
                SELECT g.id, g.org_id, (now() AT TIME ZONE o.timezone)::date AS today
                  FROM groups g
                  JOIN organizations o ON o.id = g.org_id
                 WHERE g.id = :groupId AND g.org_id = :orgId AND g.branch_id = :branchId
            )
            SELECT
                grp.today,
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
                          JOIN membership_groups ng ON ng.membership_id = n.id AND ng.group_id = grp.id
                         WHERE n.client_id = c.id AND n.org_id = grp.org_id AND n.state = 'INACTIVE'
                           AND n.id IS DISTINCT FROM m.id) AS has_next,
                (SELECT COUNT(*)::int
                   FROM session_attendance a
                   JOIN sessions s ON s.id = a.session_id
                  WHERE a.client_id = c.id AND a.presence = 'present'
                    AND s.group_id = grp.id AND s.org_id = grp.org_id AND s.status = 'completed'
                    AND NOT EXISTS (SELECT 1 FROM membership_debits d
                                     WHERE d.session_id = s.id AND d.client_id = c.id
                                       AND d.reversed_at IS NULL)) AS unpaid_sessions,
                COALESCE((SELECT j.balance_after
                            FROM client_balance_journal j
                           WHERE j.client_id = c.id AND j.org_id = grp.org_id
                           ORDER BY j.created_at DESC
                           LIMIT 1), 0) AS balance
            FROM grp
            LEFT JOIN enrollments e ON e.group_id = grp.id AND e.left_at IS NULL
            LEFT JOIN clients c ON c.id = e.client_id
            LEFT JOIN LATERAL (
                SELECT mm.*
                  FROM memberships mm
                  JOIN membership_groups mg ON mg.membership_id = mm.id AND mg.group_id = grp.id
                 WHERE mm.client_id = c.id AND mm.org_id = grp.org_id AND mm.state IN ('ACTIVE', 'INACTIVE')
                 ORDER BY CASE
                              WHEN mm.state = 'INACTIVE' THEN 1
                              WHEN mm.end_date < grp.today OR mm.sessions_remaining <= 0 THEN 2
                              ELSE 0
                          END,
                          mm.end_date NULLS LAST, mm.issued_on, mm.created_at, mm.id
                 LIMIT 1
            ) m ON TRUE
            ORDER BY c.name, c.id
            """.trimIndent()
    }
}
