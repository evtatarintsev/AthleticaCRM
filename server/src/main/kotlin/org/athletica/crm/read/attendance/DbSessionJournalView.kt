package org.athletica.crm.read.attendance

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.Row
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.attendance.JournalParticipantSchema
import org.athletica.crm.api.schemas.attendance.SessionJournalResponse
import org.athletica.crm.api.schemas.schedule.ScheduleCoachSchema
import org.athletica.crm.api.schemas.schedule.ScheduleGroupSchema
import org.athletica.crm.api.schemas.schedule.ScheduleHallSchema
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.entityids.toGroupId
import org.athletica.crm.core.entityids.toHallId
import org.athletica.crm.core.entityids.toSessionId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.sessions.SessionStatus
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asLocalDate
import org.athletica.crm.storage.asLocalTime
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/** Реализация [SessionJournalView] одним SQL: состав и тренеры собираются JSONB-агрегатами. */
class DbSessionJournalView : SessionJournalView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun journal(sessionId: SessionId): SessionJournalResponse =
        tr
            .sql(
                """
                WITH s AS (
                    SELECT s.id, s.org_id, s.group_id, g.name AS group_name, s.date, s.start_time, s.end_time,
                           s.status, h.id AS hall_id, h.name AS hall_name,
                           (s.date + s.start_time) AT TIME ZONE o.timezone <= now() AS has_started
                      FROM sessions s
                      JOIN groups g ON g.id = s.group_id
                      JOIN halls h ON h.id = s.hall_id
                      JOIN organizations o ON o.id = s.org_id
                     WHERE s.id = :sessionId AND s.org_id = :orgId
                ),
                roster AS (
                    SELECT a.id, a.client_id, a.kind::text AS kind, a.presence::text AS presence
                      FROM session_attendance a
                      JOIN s ON s.id = a.session_id
                    UNION ALL
                    SELECT NULL::uuid, e.client_id, 'regular', NULL
                      FROM (SELECT DISTINCT en.client_id
                              FROM enrollments en
                              JOIN s ON en.group_id = s.group_id
                             WHERE s.status = 'scheduled'
                               AND en.enrolled_at::date <= s.date
                               AND (en.left_at IS NULL OR en.left_at::date >= s.date)) e
                     WHERE NOT EXISTS (SELECT 1 FROM session_attendance a
                                        WHERE a.session_id = :sessionId AND a.client_id = e.client_id)
                )
                SELECT s.id, s.group_id, s.group_name, s.date, s.start_time, s.end_time,
                       s.status::text AS status, s.hall_id, s.hall_name, s.has_started,
                       (SELECT COALESCE(jsonb_agg(jsonb_build_object('id', e.id, 'name', e.name)
                                                  ORDER BY e.name, e.id), '[]'::jsonb)
                          FROM session_employees se
                          JOIN employees e ON e.id = se.employee_id
                         WHERE se.session_id = s.id) AS coaches,
                       (SELECT COALESCE(jsonb_agg(jsonb_build_object(
                                   'clientId', c.id, 'name', c.name, 'kind', upper(r.kind),
                                   'presence', upper(r.presence), 'labels', ${AttendanceSql.labelsJson("r")}
                               ) ORDER BY c.name, c.id), '[]'::jsonb)
                          FROM roster r
                          JOIN clients c ON c.id = r.client_id AND c.org_id = s.org_id) AS participants
                  FROM s
                """.trimIndent(),
            )
            .bind("sessionId", sessionId)
            .bind("orgId", ctx.orgId)
            .firstOrNull { row -> row.toJournal() }
            ?: raise(CommonDomainError("SESSION_NOT_FOUND", "Занятие не найдено"))

    /** Собирает журнал из строки выборки. */
    private fun Row.toJournal(): SessionJournalResponse =
        SessionJournalResponse(
            sessionId = asUuid("id").toSessionId(),
            group = ScheduleGroupSchema(id = asUuid("group_id").toGroupId(), name = asString("group_name")),
            date = asLocalDate("date"),
            startTime = asLocalTime("start_time"),
            endTime = asLocalTime("end_time"),
            hall = ScheduleHallSchema(id = asUuid("hall_id").toHallId(), name = asString("hall_name")),
            coaches = Json.decodeFromString<List<ScheduleCoachSchema>>(asString("coaches")),
            status = SessionStatus.valueOf(asString("status").uppercase()),
            hasStarted = asBoolean("has_started"),
            participants = Json.decodeFromString<List<JournalParticipantSchema>>(asString("participants")),
        )
}
