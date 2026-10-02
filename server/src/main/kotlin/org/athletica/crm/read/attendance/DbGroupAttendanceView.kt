package org.athletica.crm.read.attendance

import arrow.core.raise.context.Raise
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.attendance.AttendanceSessionSchema
import org.athletica.crm.api.schemas.attendance.GroupAttendanceResponse
import org.athletica.crm.api.schemas.attendance.GroupAttendanceRowSchema
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString

/** Реализация [GroupAttendanceView] одним SQL: занятия и строки участников — JSONB-агрегатами. */
class DbGroupAttendanceView : GroupAttendanceView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun summary(query: GroupAttendanceQuery): GroupAttendanceResponse =
        tr
            .sql(
                """
                WITH ss AS (
                    SELECT s.id, s.date, s.start_time
                      FROM sessions s
                     WHERE s.org_id = :orgId AND s.group_id = :groupId AND s.status = 'completed'
                       AND s.date BETWEEN :from AND :to
                ),
                marks AS (
                    SELECT a.client_id, c.name,
                           COUNT(*) FILTER (WHERE a.presence = 'present') AS present_count,
                           COUNT(*) FILTER (WHERE a.presence = 'absent') AS absent_count,
                           jsonb_agg(jsonb_build_object(
                               'sessionId', a.session_id, 'presence', upper(a.presence::text),
                               'labels', ${AttendanceSql.labelsJson("a")}
                           ) ORDER BY ss.date, ss.start_time, ss.id) AS marks
                      FROM session_attendance a
                      JOIN ss ON ss.id = a.session_id
                      JOIN clients c ON c.id = a.client_id AND c.org_id = :orgId
                     WHERE a.presence IS NOT NULL
                     GROUP BY a.client_id, c.name
                )
                SELECT (SELECT COALESCE(jsonb_agg(jsonb_build_object('id', id, 'date', date, 'startTime', start_time)
                                                  ORDER BY date, start_time, id), '[]'::jsonb)
                          FROM ss) AS sessions,
                       (SELECT COALESCE(jsonb_agg(jsonb_build_object(
                                   'clientId', client_id, 'name', name, 'presentCount', present_count,
                                   'absentCount', absent_count, 'marks', marks
                               ) ORDER BY name, client_id), '[]'::jsonb)
                          FROM marks) AS participants
                """.trimIndent(),
            )
            .bind("orgId", ctx.orgId)
            .bind("groupId", query.groupId)
            .bind("from", query.from)
            .bind("to", query.to)
            .firstOrNull { row ->
                GroupAttendanceResponse(
                    sessions = Json.decodeFromString<List<AttendanceSessionSchema>>(row.asString("sessions")),
                    participants = Json.decodeFromString<List<GroupAttendanceRowSchema>>(row.asString("participants")),
                )
            } ?: GroupAttendanceResponse(sessions = emptyList(), participants = emptyList())
}
