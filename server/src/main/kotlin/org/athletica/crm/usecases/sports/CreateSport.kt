package org.athletica.crm.usecases.sports

import arrow.core.Either
import arrow.core.raise.either
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import org.athletica.crm.api.schemas.sports.CreateSportRequest
import org.athletica.crm.api.schemas.sports.SportDetailResponse
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database

/**
 * Создаёт новый вид спорта в организации из [ctx].
 * Название уникально в рамках организации — повтор возвращает ошибку [CommonDomainError].
 */
context(db: Database, ctx: RequestContext)
suspend fun createSport(request: CreateSportRequest): Either<CommonDomainError, SportDetailResponse> =
    either {
        try {
            db
                .sql("INSERT INTO sports (id, org_id, name) VALUES (:id, :orgId, :name)")
                .bind("id", request.id)
                .bind("orgId", ctx.orgId.value)
                .bind("name", request.name)
                .execute()
        } catch (e: R2dbcDataIntegrityViolationException) {
            raise(CommonDomainError("SPORT_ALREADY_EXISTS", "Вид спорта с таким названием уже существует"))
        }

        SportDetailResponse(
            id = request.id,
            name = request.name,
        )
    }
