package org.athletica.crm.read.home

import arrow.core.raise.context.Raise
import io.r2dbc.spi.Row
import org.athletica.crm.api.schemas.home.UnclosedSessionItem
import org.athletica.crm.api.schemas.home.UnclosedSessionsResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.toSessionId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLocalDate
import org.athletica.crm.storage.asLocalTime
import org.athletica.crm.storage.asLong
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid

/**
 * Реализация [UnclosedSessionsView] поверх PostgreSQL. Окончание занятия переводится из местного
 * времени организации в момент времени в SQL; филиал определяется группой занятия.
 */
class DbUnclosedSessionsView : UnclosedSessionsView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun unclosed(query: UnclosedSessionsQuery): UnclosedSessionsResponse {
        val sessions =
            tr
                .sql("$SELECT $FROM ORDER BY s.date DESC, s.end_time DESC, s.id LIMIT :limit")
                .bind("orgId", ctx.orgId)
                .bind("branchId", ctx.branchId)
                .bind("now", query.now)
                .bind("limit", query.limit)
                .list { row -> row.toItem() }
        val total =
            tr
                .sql("SELECT COUNT(*) AS total $FROM")
                .bind("orgId", ctx.orgId)
                .bind("branchId", ctx.branchId)
                .bind("now", query.now)
                .firstOrNull { it.asLong("total") } ?: 0L
        return UnclosedSessionsResponse(sessions = sessions, total = total.toInt())
    }

    /** Собирает элемент списка из строки выборки. */
    private fun Row.toItem(): UnclosedSessionItem =
        UnclosedSessionItem(
            sessionId = asUuid("id").toSessionId(),
            groupName = asStringOrNull("group_name") ?: "",
            date = asLocalDate("date"),
            startTime = asLocalTime("start_time"),
            endTime = asLocalTime("end_time"),
            hallName = asStringOrNull("hall_name") ?: HALL_NOT_SET,
        )

    private companion object {
        /** Подпись зала, когда занятие не привязано к залу. */
        const val HALL_NOT_SET = "Не указан"

        val SELECT =
            """
            SELECT s.id, s.date, s.start_time, s.end_time, g.name AS group_name, h.name AS hall_name
            """.trimIndent()

        val FROM =
            """
            FROM sessions s
            JOIN groups g ON g.id = s.group_id AND g.org_id = s.org_id AND g.branch_id = :branchId
            JOIN organizations o ON o.id = s.org_id
            LEFT JOIN halls h ON h.id = s.hall_id
            WHERE s.org_id = :orgId
              AND s.status = 'scheduled'
              AND (s.date + s.end_time) AT TIME ZONE o.timezone <= CAST(:now AS timestamptz)
            """.trimIndent()
    }
}
