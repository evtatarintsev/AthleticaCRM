package org.athletica.crm.read.clientapp

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.athletica.crm.api.schemas.clientapp.ClientAppVisitSchema
import org.athletica.crm.api.schemas.clientapp.ClientAppVisitsResponse
import org.athletica.crm.core.ClientProfileContext
import org.athletica.crm.core.ClientProfileContexts
import org.athletica.crm.core.attendance.AttendancePresence
import org.athletica.crm.core.entityids.toSessionId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLocalDate
import org.athletica.crm.storage.asLocalTime
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid

/**
 * [ClientAppVisitsView]: «сегодня» организации с проверкой подключённого доступа, затем строки журнала
 * клиента по проведённым занятиям месяца. Метки и заметки сотрудников в выборку не попадают.
 */
class DbClientAppVisitsView : ClientAppVisitsView {
    context(ctx: ClientProfileContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun visits(query: ClientAppVisitsQuery): ClientAppVisitsResponse {
        val today =
            tr.sql(TODAY_SQL)
                .bind("now", query.now)
                .bind("accountId", ctx.accountId)
                .bind("orgId", ctx.orgId)
                .bind("clientId", ctx.clientId)
                .firstOrNull { it.asLocalDate("today") }
                ?: raise(ClientProfileContexts.clientNotFound(ctx.lang))
        val day = query.monthOf ?: today
        val monthStart = LocalDate(day.year, day.month, 1)
        val visits =
            tr.sql(VISITS_SQL)
                .bind("orgId", ctx.orgId)
                .bind("clientId", ctx.clientId)
                .bind("from", monthStart)
                .bind("to", monthStart.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)))
                .list {
                    ClientAppVisitSchema(
                        sessionId = it.asUuid("id").toSessionId(),
                        date = it.asLocalDate("date"),
                        startTime = it.asLocalTime("start_time"),
                        groupName = it.asStringOrNull("group_name"),
                        presence = AttendancePresence.valueOf(it.asString("presence").uppercase()),
                    )
                }
        return ClientAppVisitsResponse(monthStart, visits, visits.count { it.presence == AttendancePresence.PRESENT })
    }

    private companion object {
        val TODAY_SQL =
            """
            SELECT (CAST(:now AS timestamptz) AT TIME ZONE o.timezone)::date AS today
            FROM organizations o
            WHERE o.id = :orgId AND EXISTS (${ClientProfileContexts.CONNECTED_ACCESS_IDS})
            """.trimIndent()

        val VISITS_SQL =
            """
            SELECT s.id, s.date, s.start_time, g.name AS group_name, a.presence::text AS presence
            FROM session_attendance a
            JOIN sessions s ON s.id = a.session_id
            LEFT JOIN groups g ON g.id = s.group_id
            WHERE s.org_id = :orgId AND a.client_id = :clientId AND s.status = 'completed'
              AND s.date BETWEEN :from AND :to AND a.presence IS NOT NULL
            ORDER BY s.date, s.start_time, s.id
            """.trimIndent()
    }
}
