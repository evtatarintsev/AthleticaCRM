package org.athletica.crm.usecases.clients

import arrow.core.Either
import arrow.core.raise.either
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import org.athletica.crm.api.schemas.clients.ClientDetailResponse
import org.athletica.crm.api.schemas.clients.CreateClientRequest
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import kotlin.uuid.toJavaUuid

/**
 * Создаёт нового клиента в организации из [ctx] по данным [request].
 * Возвращает обновлённый список всех клиентов организации, либо ошибку если клиент уже существует.
 */
context(db: Database, ctx: RequestContext)
suspend fun createClient(request: CreateClientRequest): Either<CommonDomainError, ClientDetailResponse> =
    either {
        try {
            db
                .sql("INSERT INTO clients (id, org_id, name, avatar_id) VALUES (:id, :orgId, :name, :avatarId)")
                .bind("id", request.id)
                .bind("orgId", ctx.orgId.value)
                .bind("name", request.name)
                .bind("avatarId", request.avatarId?.toJavaUuid())
                .execute()
        } catch (e: R2dbcDataIntegrityViolationException) {
            raise(CommonDomainError("CLIENT_ALREADY_EXISTS", "Клиент с таким идентификатором уже существует"))
        }

        ClientDetailResponse(
            id = request.id,
            name = request.name,
            avatarId = request.avatarId,
        )
    }
