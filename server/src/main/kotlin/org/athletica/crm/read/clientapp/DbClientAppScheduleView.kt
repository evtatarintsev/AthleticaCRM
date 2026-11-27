package org.athletica.crm.read.clientapp

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.Row
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.athletica.crm.api.appJson
import org.athletica.crm.api.schemas.clientapp.ClientAppMovedFromSchema
import org.athletica.crm.api.schemas.clientapp.ClientAppScheduleResponse
import org.athletica.crm.api.schemas.clientapp.ClientAppScheduleSessionSchema
import org.athletica.crm.api.schemas.schedule.ScheduleCoachSchema
import org.athletica.crm.core.ClientProfileContext
import org.athletica.crm.core.ClientProfileContexts
import org.athletica.crm.core.entityids.toSessionId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.sessions.SessionStatus
import org.athletica.crm.read.SessionSql
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asInstantOrNull
import org.athletica.crm.storage.asLocalDate
import org.athletica.crm.storage.asLocalDateOrNull
import org.athletica.crm.storage.asLocalTime
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid

/**
 * [ClientAppScheduleView]: «сегодня» организации с проверкой подключённого доступа, затем занятия недели
 * и ближайшее занятие одним SQL-шаблоном — занятия, на которых клиент числится ([ClientProfileContexts.IN_SESSION]).
 */
class DbClientAppScheduleView : ClientAppScheduleView {
    context(ctx: ClientProfileContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun schedule(query: ClientAppScheduleQuery): ClientAppScheduleResponse {
        val today =
            tr.sql(TODAY_SQL)
                .bind("now", query.now)
                .bind("accountId", ctx.accountId)
                .bind("orgId", ctx.orgId)
                .bind("clientId", ctx.clientId)
                .firstOrNull { it.asLocalDate("today") }
                ?: raise(ClientProfileContexts.clientNotFound(ctx.lang))
        val day = query.weekOf ?: today
        val weekStart = day.minus(day.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
        val sessions =
            tr.sql(sessionsSql("s.date BETWEEN :from AND :to", ""))
                .bind("now", query.now)
                .bind("orgId", ctx.orgId)
                .bind("clientId", ctx.clientId)
                .bind("from", weekStart)
                .bind("to", weekStart.plus(DatePeriod(days = 6)))
                .list { it.toSession() }
        val next =
            tr.sql(sessionsSql("s.status = 'scheduled' AND NOT $HAS_STARTED", "LIMIT 1"))
                .bind("now", query.now)
                .bind("orgId", ctx.orgId)
                .bind("clientId", ctx.clientId)
                .firstOrNull { it.toSession() }
        return ClientAppScheduleResponse(weekStart, today, sessions, next)
    }

    /** Занятие из строки выборки. */
    private fun Row.toSession(): ClientAppScheduleSessionSchema =
        ClientAppScheduleSessionSchema(
            sessionId = asUuid("id").toSessionId(),
            date = asLocalDate("date"),
            startTime = asLocalTime("start_time"),
            endTime = asLocalTime("end_time"),
            groupName = asStringOrNull("group_name"),
            hallName = asString("hall_name"),
            coaches = appJson.decodeFromString<List<ScheduleCoachSchema>>(asString("coaches")).map { it.name },
            status = SessionStatus.valueOf(asString("status").uppercase()),
            movedFrom = asLocalDateOrNull("moved_from_date")?.let { ClientAppMovedFromSchema(it, asLocalTime("moved_from_time")) },
            hasStarted = asBoolean("has_started"),
            absenceNoticeAt = asInstantOrNull("notice_at"),
        )

    private companion object {
        const val HAS_STARTED = "(s.date + s.start_time) AT TIME ZONE o.timezone <= CAST(:now AS timestamptz)"

        /** Перенос: занятие расписания стоит не на предписанные слотом дату и время. */
        const val MOVED = "s.is_rescheduled AND (s.origin_date, ss.start_time) IS DISTINCT FROM (s.date, s.start_time)"

        val TODAY_SQL =
            """
            SELECT (CAST(:now AS timestamptz) AT TIME ZONE o.timezone)::date AS today
            FROM organizations o
            WHERE o.id = :orgId AND EXISTS (${ClientProfileContexts.CONNECTED_ACCESS_IDS})
            """.trimIndent()

        /** Занятия клиента организации по условию [where] по дате и времени, с хвостом [tail]. */
        fun sessionsSql(
            where: String,
            tail: String,
        ) = """
            SELECT s.id, s.date, s.start_time, s.end_time, s.status::text AS status, g.name AS group_name, h.name AS hall_name,
                   ${SessionSql.COACHES_JSON}::text AS coaches,
                   $HAS_STARTED AS has_started,
                   CASE WHEN ss.id IS NOT NULL AND $MOVED THEN s.origin_date END AS moved_from_date,
                   CASE WHEN ss.id IS NOT NULL AND $MOVED THEN ss.start_time END AS moved_from_time,
                   n.created_at AS notice_at
            FROM sessions s
            JOIN organizations o ON o.id = s.org_id
            JOIN halls h ON h.id = s.hall_id
            LEFT JOIN groups g ON g.id = s.group_id
            LEFT JOIN schedule_slots ss ON ss.id = s.origin_slot_id
            LEFT JOIN client_app_absence_notices n ON n.session_id = s.id AND n.client_id = :clientId
            WHERE s.org_id = :orgId AND ${ClientProfileContexts.IN_SESSION} AND $where
            ORDER BY s.date, s.start_time, s.id
            $tail
            """.trimIndent()
    }
}
