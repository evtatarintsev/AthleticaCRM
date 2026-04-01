package org.athletica.crm.usecases.sports

import arrow.core.Either
import arrow.core.raise.either
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import org.athletica.crm.api.schemas.sports.SportDetailResponse
import org.athletica.crm.api.schemas.sports.UpdateSportRequest
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import kotlin.uuid.toKotlinUuid

/**
 * Обновляет название вида спорта.
 * Возвращает ошибку если вид спорта не найден в организации из [ctx]
 * или новое название уже занято другой записью.
 */
context(db: Database, ctx: RequestContext)
suspend fun updateSport(request: UpdateSportRequest): Either<CommonDomainError, SportDetailResponse> =
    either {
        // Проверяем существование записи и принадлежность организации
        db
            .sql("SELECT id FROM sports WHERE id = :id AND org_id = :orgId")
            .bind("id", request.id)
            .bind("orgId", ctx.orgId.value)
            .firstOrNull { row, _ -> row.get("id", java.util.UUID::class.java)!!.toKotlinUuid() }
            ?: raise(CommonDomainError("SPORT_NOT_FOUND", "Вид спорта не найден"))

        try {
            db
                .sql("UPDATE sports SET name = :name WHERE id = :id AND org_id = :orgId")
                .bind("id", request.id)
                .bind("orgId", ctx.orgId.value)
                .bind("name", request.name)
                .execute()
        } catch (e: R2dbcDataIntegrityViolationException) {
            raise(CommonDomainError("SPORT_NAME_ALREADY_EXISTS", "Вид спорта с таким названием уже существует"))
        }

        SportDetailResponse(
            id = request.id,
            name = request.name,
        )
    }
