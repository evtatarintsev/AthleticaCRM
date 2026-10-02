package org.athletica.crm.read.attendance

import arrow.core.raise.context.Raise
import io.r2dbc.spi.Row
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.attendance.AttendanceLabelSchema
import org.athletica.crm.api.schemas.attendance.ClientAttendanceItemSchema
import org.athletica.crm.api.schemas.attendance.ClientAttendanceResponse
import org.athletica.crm.api.schemas.schedule.ScheduleGroupSchema
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.attendance.AttendancePresence
import org.athletica.crm.core.entityids.toGroupId
import org.athletica.crm.core.entityids.toSessionId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLocalDate
import org.athletica.crm.storage.asLocalTime
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/** Реализация [ClientAttendanceView] одним SQL по строкам журнала проведённых занятий. */
class DbClientAttendanceView : ClientAttendanceView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(query: ClientAttendanceQuery): ClientAttendanceResponse =
        tr
            .sql(
                """
                SELECT s.id, s.date, s.start_time, g.id AS group_id, g.name AS group_name,
                       a.presence::text AS presence, ${AttendanceSql.labelsJson("a")} AS labels
                  FROM session_attendance a
                  JOIN sessions s ON s.id = a.session_id
                  JOIN groups g ON g.id = s.group_id
                 WHERE s.org_id = :orgId AND a.client_id = :clientId AND s.status = 'completed'
                   AND s.date BETWEEN :from AND :to AND a.presence IS NOT NULL
                 ORDER BY s.date, s.start_time, s.id
                """.trimIndent(),
            )
            .bind("orgId", ctx.orgId)
            .bind("clientId", query.clientId)
            .bind("from", query.from)
            .bind("to", query.to)
            .list { it.toItem() }
            .let { ClientAttendanceResponse(items = it) }

    /** Собирает отметку клиента из строки выборки. */
    private fun Row.toItem(): ClientAttendanceItemSchema =
        ClientAttendanceItemSchema(
            sessionId = asUuid("id").toSessionId(),
            group = ScheduleGroupSchema(id = asUuid("group_id").toGroupId(), name = asString("group_name")),
            date = asLocalDate("date"),
            startTime = asLocalTime("start_time"),
            presence = AttendancePresence.valueOf(asString("presence").uppercase()),
            labels = Json.decodeFromString<List<AttendanceLabelSchema>>(asString("labels")),
        )
}
