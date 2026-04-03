package org.athletica.crm.usecases.sports

import arrow.core.Either
import arrow.core.raise.either
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.sports.SportDetailResponse
import org.athletica.crm.api.schemas.sports.UpdateSportRequest
import org.athletica.crm.audit.AuditLog
import org.athletica.crm.audit.logUpdate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database

/**
 * Обновляет название вида спорта одним запросом.
 * Если UPDATE затронул 0 строк — запись не найдена в организации из [ctx].
 * Нарушение уникального индекса означает, что новое название уже занято.
 */
context(db: Database, ctx: RequestContext, audit: AuditLog)
suspend fun updateSport(request: UpdateSportRequest): Either<CommonDomainError, SportDetailResponse> =
    either {
        val updatedRows =
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

        if (updatedRows == 0L) raise(CommonDomainError("SPORT_NOT_FOUND", "Вид спорта не найден"))

        SportDetailResponse(
            id = request.id,
            name = request.name,
        ).also { audit.logUpdate(it) }
    }

/** Логирует обновление вида спорта: тип сущности `"sport"`, данные — JSON-снапшот [result] после изменения. */
context(ctx: RequestContext)
fun AuditLog.logUpdate(result: SportDetailResponse) = logUpdate("sport", result.id, Json.encodeToString(result))
