package org.athletica.crm.routes

import org.athletica.crm.api.schemas.audit.AuditLogItem
import org.athletica.crm.api.schemas.audit.AuditLogListRequest
import org.athletica.crm.api.schemas.audit.AuditLogListResponse
import org.athletica.crm.domain.audit.AuditActionType
import org.athletica.crm.domain.audit.AuditFilter
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.storage.Database

/**
 * Регистрирует маршруты для модуля аудита.
 * GET /audit/log — список действий с пагинацией и фильтрами.
 */
context(db: Database)
fun RouteWithContext.auditRoutes(audit: AuditLog) {
    route("/audit") {
        get<AuditLogListRequest, AuditLogListResponse>("/log") { request ->
            val page = request.page.coerceAtLeast(0)
            val pageSize = request.pageSize.coerceIn(1, 200)

            val filter =
                AuditFilter(
                    limit = pageSize.toUInt(),
                    offset = (page * pageSize).toUInt(),
                    actionType =
                        request.actionType?.let { code ->
                            AuditActionType.entries.firstOrNull { it.code == code }
                        },
                    userId = request.userId,
                    entityType = request.entityType,
                    from = request.from,
                    to = request.to,
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
