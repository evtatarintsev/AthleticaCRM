package org.athletica.crm.read.home

import arrow.core.raise.context.Raise
import io.r2dbc.spi.Row
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import org.athletica.crm.api.schemas.home.TodaySessionItem
import org.athletica.crm.api.schemas.home.TodaySessionsResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.toSessionId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLocalTime
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid

/**
 * Реализация [TodayScheduleView] поверх PostgreSQL. Названия группы и зала приходят
 * join'ами, фильтр по статусу и сортировка выполняются в SQL.
 */
class DbTodayScheduleView : TodayScheduleView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun sessionsOn(date: LocalDate): TodaySessionsResponse {
        val sessions =
            tr
                .sql(SQL)
                .bind("orgId", ctx.orgId)
                .bind("date", date.toJavaLocalDate())
                .list { row -> row.toItem() }
        return TodaySessionsResponse(date = date, sessions = sessions)
    }

    /** Собирает элемент расписания из строки выборки. */
    private fun Row.toItem(): TodaySessionItem =
        TodaySessionItem(
            sessionId = asUuid("id").toSessionId(),
            groupName = asStringOrNull("group_name") ?: "",
            startTime = asLocalTime("start_time"),
            endTime = asLocalTime("end_time"),
            hallName = asStringOrNull("hall_name") ?: HALL_NOT_SET,
        )

    private companion object {
        /** Подпись зала, когда занятие не привязано к залу. */
        const val HALL_NOT_SET = "Не указан"

        val SQL =
            """
            SELECT s.id, s.start_time, s.end_time, g.name AS group_name, h.name AS hall_name
            FROM sessions s
            LEFT JOIN groups g ON g.id = s.group_id
            LEFT JOIN halls h ON h.id = s.hall_id
            WHERE s.org_id = :orgId
              AND s.date = :date
              AND s.status = 'scheduled'
            ORDER BY s.start_time, s.id
            """.trimIndent()
    }
}
