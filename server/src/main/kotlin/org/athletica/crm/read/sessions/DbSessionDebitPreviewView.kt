package org.athletica.crm.read.sessions

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.Row
import org.athletica.crm.api.schemas.attendance.DebitMembershipSchema
import org.athletica.crm.api.schemas.attendance.DebitedParticipantDebitSchema
import org.athletica.crm.api.schemas.attendance.NoMembershipParticipantDebitSchema
import org.athletica.crm.api.schemas.attendance.NotDebitedParticipantDebitSchema
import org.athletica.crm.api.schemas.attendance.ParticipantDebitSchema
import org.athletica.crm.api.schemas.attendance.ParticipantDebitStateSchema
import org.athletica.crm.api.schemas.attendance.SessionDebitPreviewResponse
import org.athletica.crm.api.schemas.attendance.WillDebitParticipantDebitSchema
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.entityids.toMembershipId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull
import org.athletica.crm.storage.sql.MembershipCandidateSql

/**
 * Реализация [SessionDebitPreviewView] одним SQL: состав занятия (отметки журнала и, для
 * запланированного занятия, записанные в группу), действующее списание и кандидат подбора
 * через `LATERAL` с условием и порядком из [MembershipCandidateSql].
 */
class DbSessionDebitPreviewView : SessionDebitPreviewView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun preview(sessionId: SessionId): SessionDebitPreviewResponse {
        tr
            .sql("SELECT 1 FROM sessions WHERE id = :sessionId AND org_id = :orgId")
            .bind("sessionId", sessionId)
            .bind("orgId", ctx.orgId)
            .firstOrNull { 1 }
            ?: raise(CommonDomainError("SESSION_NOT_FOUND", Messages.SessionNotFound.localize()))
        val participants =
            tr
                .sql(SQL)
                .bind("sessionId", sessionId)
                .bind("orgId", ctx.orgId)
                .list { it.toParticipant() }
        return SessionDebitPreviewResponse(participants)
    }

    /** Списание участника из строки результата. */
    private fun Row.toParticipant(): ParticipantDebitSchema =
        ParticipantDebitSchema(
            clientId = asUuid("client_id").toClientId(),
            debit = debitState(),
        )

    /** Состояние списания: фактическое для проведённого занятия, кандидат — для запланированного. */
    private fun Row.debitState(): ParticipantDebitStateSchema {
        val debitedId = asUuidOrNull("debited_id")
        val candidateId = asUuidOrNull("candidate_id")
        return when {
            debitedId != null -> DebitedParticipantDebitSchema(membership("debited"))
            asString("status") == "completed" ->
                if (asStringOrNull("presence") == "present") {
                    NoMembershipParticipantDebitSchema
                } else {
                    NotDebitedParticipantDebitSchema
                }
            candidateId != null ->
                WillDebitParticipantDebitSchema(membership("candidate"), activates = asString("candidate_state") == "INACTIVE")
            else -> NoMembershipParticipantDebitSchema
        }
    }

    /** Абонемент из колонок с префиксом [prefix]. */
    private fun Row.membership(prefix: String): DebitMembershipSchema =
        DebitMembershipSchema(
            id = asUuid("${prefix}_id").toMembershipId(),
            name = asString("${prefix}_name"),
            sessionsRemaining = get("${prefix}_remaining", Int::class.javaObjectType),
            sessionsTotal = get("${prefix}_total", Int::class.javaObjectType),
        )

    private companion object {
        /** Состав занятия, действующее списание и кандидат подбора для каждого участника. */
        val SQL =
            """
            WITH s AS (
                SELECT s.id, s.org_id, s.group_id, s.date, s.status::text AS status,
                       (now() AT TIME ZONE o.timezone)::date AS today
                  FROM sessions s
                  JOIN organizations o ON o.id = s.org_id
                 WHERE s.id = :sessionId AND s.org_id = :orgId
            ),
            roster AS (
                SELECT a.client_id, a.presence::text AS presence
                  FROM session_attendance a
                  JOIN s ON s.id = a.session_id
                UNION ALL
                SELECT e.client_id, NULL
                  FROM (SELECT DISTINCT en.client_id
                          FROM enrollments en
                          JOIN s ON en.group_id = s.group_id
                         WHERE s.status = 'scheduled'
                           AND en.enrolled_at::date <= s.date
                           AND (en.left_at IS NULL OR en.left_at::date >= s.date)) e
                 WHERE NOT EXISTS (SELECT 1 FROM session_attendance a
                                    WHERE a.session_id = :sessionId AND a.client_id = e.client_id)
            )
            SELECT r.client_id, r.presence, s.status,
                   dm.id AS debited_id, dm.name AS debited_name,
                   dm.sessions_remaining AS debited_remaining, dm.sessions_total AS debited_total,
                   c.id AS candidate_id, c.name AS candidate_name, c.state AS candidate_state,
                   c.sessions_remaining AS candidate_remaining, c.sessions_total AS candidate_total
              FROM roster r
             CROSS JOIN s
              LEFT JOIN membership_debits d
                     ON d.session_id = s.id AND d.client_id = r.client_id AND d.reversed_at IS NULL
              LEFT JOIN memberships dm ON dm.id = d.membership_id
              LEFT JOIN LATERAL (
                  SELECT m.id, m.name, m.state, m.sessions_remaining, m.sessions_total
                    FROM memberships m
                   WHERE s.status = 'scheduled' AND s.group_id IS NOT NULL
                     AND ${MembershipCandidateSql.condition("r.client_id", "s.group_id", "s.date", "s.today")}
                   ORDER BY ${MembershipCandidateSql.ORDER}
                   LIMIT 1
              ) c ON TRUE
             ORDER BY r.client_id
            """.trimIndent()
    }
}
