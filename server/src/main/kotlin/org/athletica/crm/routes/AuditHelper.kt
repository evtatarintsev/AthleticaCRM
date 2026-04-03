package org.athletica.crm.routes

import io.ktor.server.routing.RoutingCall
import org.athletica.crm.audit.AuditActionType
import org.athletica.crm.audit.AuditEvent
import org.athletica.crm.audit.AuditService
import org.athletica.crm.core.RequestContext
import kotlin.uuid.Uuid

/**
 * Возвращает IP-адрес клиента из заголовка X-Forwarded-For (для запросов через прокси)
 * или из локального адреса прямого подключения.
 */
fun RoutingCall.clientIp(): String? =
    request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
        ?: request.local.remoteHost

/**
 * Логирует событие аудита из текущего контекста запроса.
 * Неблокирующий вызов (channel.trySend).
 */
context(ctx: RequestContext, audit: AuditService)
fun auditLog(
    actionType: AuditActionType,
    entityType: String? = null,
    entityId: Uuid? = null,
    ipAddress: String? = null,
) {
    audit.log(
        AuditEvent(
            orgId = ctx.orgId,
            userId = ctx.userId,
            username = ctx.username,
            actionType = actionType,
            entityType = entityType,
            entityId = entityId,
            ipAddress = ipAddress,
        ),
    )
}
