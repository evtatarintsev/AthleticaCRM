package org.athletica.crm.routes

import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import org.athletica.crm.db.Database
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.PostgresAuditLog
import org.athletica.crm.usecases.audit.AuditLogListRequest
import org.athletica.crm.usecases.audit.auditLogList

/**
 * Регистрирует маршруты для модуля аудита.
 * GET /audit/log — список действий с пагинацией и фильтрами.
 * Требует контекстных параметров [Database] и [PostgresAuditLog].
 */
context(db: Database, audit: AuditLog)
fun Route.auditRoutes() {
    route("/audit") {
        getWithContext("/log") {
            call.eitherToResponse {
                val params = call.request.queryParameters
                val request =
                    AuditLogListRequest(
                        page = params["page"]?.toIntOrNull() ?: 0,
                        pageSize = params["pageSize"]?.toIntOrNull()?.coerceIn(1, 200) ?: 50,
                        actionType = params["actionType"],
                        userId = params["userId"],
                        entityType = params["entityType"],
                        from = params["from"],
                        to = params["to"],
                    )
                auditLogList(request).bind()
            }
        }
    }
}
