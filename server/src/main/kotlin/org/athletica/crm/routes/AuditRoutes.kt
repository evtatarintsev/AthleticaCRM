package org.athletica.crm.routes

import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.audit.AuditLogItem
import org.athletica.crm.api.schemas.audit.AuditLogListResponse
import org.athletica.crm.core.entityids.toUserId
import org.athletica.crm.domain.audit.AuditActionType
import org.athletica.crm.domain.audit.AuditFilter
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.storage.Database
import kotlin.uuid.Uuid

/**
 * Регистрирует маршруты для модуля аудита.
 * GET /audit/log — список действий с пагинацией и фильтрами.
 */
context(db: Database)
fun RouteWithContext.auditRoutes(audit: AuditLog) {
    route("/audit") {
        get<AuditLogListResponse>("/log") { call ->
            val params = call.request.queryParameters
            val page = params["page"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val pageSize = params["pageSize"]?.toIntOrNull()?.coerceIn(1, 200) ?: 50

            val filter =
                AuditFilter(
                    limit = pageSize.toUInt(),
                    offset = (page * pageSize).toUInt(),
                    actionType =
                        params["actionType"]?.let { code ->
                            AuditActionType.entries.firstOrNull { it.code == code }
                        },
                    userId = params["userId"]?.let { runCatching { Uuid.parse(it).toUserId() }.getOrNull() },
                    entityType = params["entityType"],
                    from = params["from"],
                    to = params["to"],
                )

            db.transaction {
                val items = audit.list(filter)
                val total = audit.count(filter)
                AuditLogListResponse(
                    items =
                        items.map { event ->
                            AuditLogItem(
                                id = event.id!!,
                                userId = event.userId?.value,
                                username = event.username,
                                actionType = event.actionType.code,
                                entityType = event.entityType,
                                entityId = event.entityId,
                                data = event.data,
                                ipAddress = event.ipAddress,
                                createdAt = event.createdAt!!,
                            )
                        },
                    total = total,
                    page = page,
                    pageSize = pageSize,
                )
            }
        }
    }
}
