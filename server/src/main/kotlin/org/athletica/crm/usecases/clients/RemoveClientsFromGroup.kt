package org.athletica.crm.usecases.clients

import arrow.core.Either
import arrow.core.raise.either
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.clients.RemoveClientFromGroupRequest
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logUpdate
import org.athletica.crm.i18n.Messages

/**
 * Удаляет список клиентов из группы.
 * Проверяет, что группа принадлежит организации из [ctx].
 * Возвращает ошибку если группа не найдена.
 */
context(db: Database, ctx: RequestContext, audit: AuditLog)
suspend fun removeClientsFromGroup(request: RemoveClientFromGroupRequest): Either<CommonDomainError, Unit> =
    either {
        db
            .sql("SELECT id FROM groups WHERE id = :groupId AND org_id = :orgId")
            .bind("groupId", request.groupId)
            .bind("orgId", ctx.orgId.value)
            .firstOrNull { _ -> true }
            ?: raise(CommonDomainError("GROUP_NOT_FOUND", Messages.GroupNotFound.localize()))

        val auditData = Json.encodeToString(request)
        db.transaction {
            for (clientId in request.clientIds) {
                sql("DELETE FROM client_groups WHERE client_id = :clientId AND group_id = :groupId")
                    .bind("clientId", clientId)
                    .bind("groupId", request.groupId)
                    .execute()
            }
        }

        request.clientIds.forEach { clientId ->
            audit.logUpdate("client", clientId, auditData)
        }
    }
