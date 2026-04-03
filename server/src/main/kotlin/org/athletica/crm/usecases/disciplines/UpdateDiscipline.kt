package org.athletica.crm.usecases.disciplines

import arrow.core.Either
import arrow.core.raise.either
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.disciplines.DisciplineDetailResponse
import org.athletica.crm.api.schemas.disciplines.UpdateDisciplineRequest
import org.athletica.crm.audit.AuditLog
import org.athletica.crm.audit.logUpdate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database

/**
 * Обновляет название дисциплины одним запросом.
 * Если UPDATE затронул 0 строк — запись не найдена в организации из [ctx].
 * Нарушение уникального индекса означает, что новое название уже занято.
 */
context(db: Database, ctx: RequestContext, audit: AuditLog)
suspend fun updateDiscipline(request: UpdateDisciplineRequest): Either<CommonDomainError, DisciplineDetailResponse> =
    either {
        val updatedRows =
            try {
                db
                    .sql("UPDATE disciplines SET name = :name WHERE id = :id AND org_id = :orgId")
                    .bind("id", request.id)
                    .bind("orgId", ctx.orgId.value)
                    .bind("name", request.name)
                    .execute()
            } catch (e: R2dbcDataIntegrityViolationException) {
                raise(CommonDomainError("DISCIPLINE_NAME_ALREADY_EXISTS", "Дисциплина с таким названием уже существует"))
            }

        if (updatedRows == 0L) raise(CommonDomainError("DISCIPLINE_NOT_FOUND", "Дисциплина не найдена"))

        DisciplineDetailResponse(
            id = request.id,
            name = request.name,
        ).also { audit.logUpdate(it) }
    }

/** Логирует обновление дисциплины: тип сущности `"discipline"`, данные — JSON-снапшот [result] после изменения. */
context(ctx: RequestContext)
fun AuditLog.logUpdate(result: DisciplineDetailResponse) = logUpdate("discipline", result.id, Json.encodeToString(result))
