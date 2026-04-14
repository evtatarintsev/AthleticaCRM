package org.athletica.crm.usecases.clients

import arrow.core.Either
import arrow.core.raise.either
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.clients.ClientDetailResponse
import org.athletica.crm.api.schemas.clients.EditClientRequest
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logUpdate
import org.athletica.crm.i18n.Messages

/**
 * Обновляет данные существующего клиента организации из [ctx] по данным [request].
 * Возвращает обновлённые данные клиента, либо ошибку если клиент не найден.
 */
context(db: Database, ctx: RequestContext, audit: AuditLog)
suspend fun editClient(request: EditClientRequest): Either<CommonDomainError, ClientDetailResponse> =
    either {
        val updated =
            db
                .sql(
                    "UPDATE clients SET name = :name, avatar_id = :avatarId, birthday = :birthday, gender = :gender::gender " +
                        "WHERE id = :id AND org_id = :orgId",
                )
                .bind("name", request.name)
                .bind("avatarId", request.avatarId)
                .bind("birthday", request.birthday)
                .bind("gender", request.gender.name)
                .bind("id", request.id)
                .bind("orgId", ctx.orgId)
                .execute()

        if (updated == 0L) raise(CommonDomainError("CLIENT_NOT_FOUND", Messages.ClientNotFound.localize()))

        clientDetail(request.id).bind().also { audit.logUpdate(it) }
    }

/** Логирует обновление клиента: тип сущности `"client"`, данные — JSON-снапшот [result]. */
context(ctx: RequestContext)
fun AuditLog.logUpdate(result: ClientDetailResponse) = logUpdate("client", result.id, Json.encodeToString(result))
