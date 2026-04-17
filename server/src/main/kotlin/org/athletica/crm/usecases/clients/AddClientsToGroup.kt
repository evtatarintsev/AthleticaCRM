package org.athletica.crm.usecases.clients

import arrow.core.Either
import arrow.core.raise.either
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.clients.AddClientsToGroupRequest
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logUpdate
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Database

/**
 * Добавляет список клиентов из [request] в группу.
 * Проверяет, что группа принадлежит организации из [ctx].
 * Уже существующие связи клиент–группа игнорируются (ON CONFLICT DO NOTHING).
 * Возвращает ошибку если группа не найдена или один из клиентов не существует.
 */
context(db: Database, ctx: RequestContext, audit: AuditLog)
suspend fun addClientsToGroup(request: AddClientsToGroupRequest): Either<CommonDomainError, Unit> =
    either {
        db
            .sql("SELECT id FROM groups WHERE id = :groupId AND org_id = :orgId")
            .bind("groupId", request.groupId)
            .bind("orgId", ctx.orgId)
            .firstOrNull { _ -> true }
            ?: raise(CommonDomainError("GROUP_NOT_FOUND", Messages.GroupNotFound.localize()))

        try {
            db.transaction {
                for (clientId in request.clientIds) {
                    sql(
                        "INSERT INTO client_groups (client_id, group_id) VALUES (:clientId, :groupId) ON CONFLICT ON CONSTRAINT uq_client_groups DO NOTHING",
                    )
                        .bind("clientId", clientId)
                        .bind("groupId", request.groupId)
                        .execute()
                }
            }
        } catch (e: R2dbcDataIntegrityViolationException) {
            raise(CommonDomainError("CLIENT_NOT_FOUND", Messages.ClientNotFound.localize()))
        }

        val auditData = Json.encodeToString(request)
        request.clientIds.forEach {
            audit.logUpdate("client", it, auditData)
        }
    }
