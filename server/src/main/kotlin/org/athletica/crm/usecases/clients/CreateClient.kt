package org.athletica.crm.usecases.clients

import arrow.core.Either
import arrow.core.raise.either
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import kotlinx.datetime.toJavaLocalDate
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.clients.ClientDetailResponse
import org.athletica.crm.api.schemas.clients.CreateClientRequest
import org.athletica.crm.audit.AuditLog
import org.athletica.crm.audit.logCreate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import org.athletica.crm.i18n.Messages
import kotlin.uuid.toJavaUuid

/**
 * Создаёт нового клиента в организации из [ctx] по данным [request].
 * Возвращает обновлённый список всех клиентов организации, либо ошибку если клиент уже существует.
 */
context(db: Database, ctx: RequestContext, audit: AuditLog)
suspend fun createClient(request: CreateClientRequest): Either<CommonDomainError, ClientDetailResponse> =
    either {
        try {
            db
                .sql("INSERT INTO clients (id, org_id, name, avatar_id, birthday, gender) VALUES (:id, :orgId, :name, :avatarId, :birthday, :gender::gender)")
                .bind("id", request.id)
                .bind("orgId", ctx.orgId.value)
                .bind("name", request.name)
                .bind("avatarId", request.avatarId?.toJavaUuid())
                .bind("birthday", request.birthday?.toJavaLocalDate())
                .bind("gender", request.gender.name)
                .execute()
        } catch (e: R2dbcDataIntegrityViolationException) {
            raise(CommonDomainError("CLIENT_ALREADY_EXISTS", Messages.ClientAlreadyExists.localize()))
        }

        ClientDetailResponse(
            id = request.id,
            name = request.name,
            avatarId = request.avatarId,
            birthday = request.birthday,
            gender = request.gender,
            groups = emptyList(),
            balance = 0.0,
        ).also { audit.logCreate(it) }
    }

/** Логирует создание клиента: тип сущности `"client"`, данные — JSON-снапшот [result]. */
context(ctx: RequestContext)
fun AuditLog.logCreate(result: ClientDetailResponse) = logCreate("client", result.id, Json.encodeToString(result))
