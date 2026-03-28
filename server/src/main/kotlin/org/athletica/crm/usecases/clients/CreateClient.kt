package org.athletica.crm.usecases.clients

import arrow.core.Either
import arrow.core.raise.either
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import org.athletica.crm.api.schemas.clients.ClientDetailResponse
import org.athletica.crm.api.schemas.clients.CreateClientRequest
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database

/**
 * Создаёт нового клиента в организации из [ctx] по данным [request].
 * Возвращает обновлённый список всех клиентов организации, либо ошибку если клиент уже существует.
 */
context(db: Database, ctx: RequestContext)
suspend fun createClient(request: CreateClientRequest): Either<CommonDomainError, ClientDetailResponse> =
    either {
        try {
            db
                .sql("INSERT INTO clients (id, org_id, name) VALUES (:id, :orgId, :name)")
                .bind("id", request.id)
                .bind("orgId", ctx.orgId.value)
                .bind("name", request.name)
                .execute()
        } catch (e: R2dbcDataIntegrityViolationException) {
            raise(CommonDomainError("CLIENT_ALREADY_EXISTS", "Клиент с таким идентификатором уже существует"))
        }

        ClientDetailResponse(
            id = request.id,
            name = request.name,
        )
    }
