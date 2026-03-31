package org.athletica.crm.usecases.groups

import arrow.core.Either
import arrow.core.raise.either
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import org.athletica.crm.api.schemas.groups.GroupCreateRequest
import org.athletica.crm.api.schemas.groups.GroupDetailResponse
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database

/**
 * Создаёт новую группу в организации из [ctx] по данным [request].
 * Возвращает детали созданной группы, либо ошибку если группа уже существует.
 */
context(db: Database, ctx: RequestContext)
suspend fun createGroup(request: GroupCreateRequest): Either<CommonDomainError, GroupDetailResponse> =
    either {
        try {
            db
                .sql("INSERT INTO groups (id, org_id, name) VALUES (:id, :orgId, :name)")
                .bind("id", request.id)
                .bind("orgId", ctx.orgId.value)
                .bind("name", request.name)
                .execute()
        } catch (e: R2dbcDataIntegrityViolationException) {
            raise(CommonDomainError("GROUP_ALREADY_EXISTS", "Группа с таким идентификатором уже существует"))
        }

        GroupDetailResponse(
            id = request.id,
            name = request.name,
        )
    }
