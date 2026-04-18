package org.athletica.crm.usecases.clients

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.clients.RemoveClientFromGroupRequest
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logUpdate
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction

/**
 * Удаляет список клиентов из группы.
 * Проверяет, что группа принадлежит организации из [ctx].
 * Возвращает ошибку если группа не найдена.
 */
context(ctx: RequestContext, tr: Transaction, audit: AuditLog, raise: Raise<CommonDomainError>)
suspend fun removeClientsFromGroup(request: RemoveClientFromGroupRequest) {
    tr
        .sql("SELECT id FROM groups WHERE id = :groupId AND org_id = :orgId")
        .bind("groupId", request.groupId)
        .bind("orgId", ctx.orgId)
        .firstOrNull { _ -> true }
        ?: raise(CommonDomainError("GROUP_NOT_FOUND", Messages.GroupNotFound.localize()))

    val auditData = Json.encodeToString(request)
    for (clientId in request.clientIds) {
        tr.sql("DELETE FROM client_groups WHERE client_id = :clientId AND group_id = :groupId")
            .bind("clientId", clientId)
            .bind("groupId", request.groupId)
            .execute()
    }

    request.clientIds.forEach { clientId ->
        audit.logUpdate("client", clientId, auditData)
    }
}
