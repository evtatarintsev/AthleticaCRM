package org.athletica.crm.read.auth

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import org.athletica.crm.api.schemas.auth.SessionItem
import org.athletica.crm.api.schemas.auth.SessionListResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.toAuthSessionId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuidOrNull

/**
 * Реализация [SessionListView] поверх PostgreSQL. Сотрудник присоединяет свои сеансы
 * внешним соединением: пустая выборка значит, что сотрудника нет, а строка без сеанса — что сеансов нет.
 */
class DbSessionListView : SessionListView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun list(query: SessionListQuery): SessionListResponse {
        val rows =
            tr
                .sql(SQL)
                .bind("employeeId", query.employeeId)
                .bind("orgId", ctx.orgId)
                .bind("since", query.now - query.maxIdle)
                .list { row ->
                    listOfNotNull(
                        row.asUuidOrNull("id")?.toAuthSessionId()?.let { id ->
                            val device = userAgentInfo(row.asStringOrNull("user_agent"))
                            SessionItem(
                                id = id,
                                browser = device.browser,
                                os = device.os,
                                platform = device.platform,
                                ip = row.asStringOrNull("ip"),
                                createdAt = row.asInstant("created_at"),
                                lastSeenAt = row.asInstant("last_seen_at"),
                                current = id == ctx.sessionId,
                            )
                        },
                    )
                }
        ensure(rows.isNotEmpty()) { CommonDomainError("EMPLOYEE_NOT_FOUND", Messages.EmployeeNotFound.localize()) }
        return SessionListResponse(rows.flatten())
    }

    private companion object {
        /** Сотрудник с сеансами, активными после `:since`. */
        val SQL =
            """
            SELECT s.id, s.user_agent, s.ip, s.created_at, s.last_seen_at
            FROM employees e
            LEFT JOIN auth_sessions s
                   ON s.user_id = e.user_id AND s.org_id = e.org_id AND s.last_seen_at > :since
            WHERE e.id = :employeeId AND e.org_id = :orgId
            ORDER BY s.last_seen_at DESC
            """.trimIndent()
    }
}
