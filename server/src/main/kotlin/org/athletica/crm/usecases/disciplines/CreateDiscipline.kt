package org.athletica.crm.usecases.disciplines

import arrow.core.Either
import arrow.core.raise.either
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.disciplines.CreateDisciplineRequest
import org.athletica.crm.api.schemas.disciplines.DisciplineDetailResponse
import org.athletica.crm.audit.AuditLog
import org.athletica.crm.audit.logCreate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import org.athletica.crm.i18n.Messages

/**
 * Создаёт новую дисциплину в организации из [ctx].
 * Название уникально в рамках организации — повтор возвращает ошибку [CommonDomainError].
 */
context(db: Database, ctx: RequestContext, audit: AuditLog)
suspend fun createDiscipline(request: CreateDisciplineRequest): Either<CommonDomainError, DisciplineDetailResponse> =
    either {
        try {
            db
                .sql("INSERT INTO disciplines (id, org_id, name) VALUES (:id, :orgId, :name)")
                .bind("id", request.id)
                .bind("orgId", ctx.orgId.value)
                .bind("name", request.name)
                .execute()
        } catch (e: R2dbcDataIntegrityViolationException) {
            raise(CommonDomainError("DISCIPLINE_ALREADY_EXISTS", Messages.DisciplineAlreadyExists.localize()))
        }

        DisciplineDetailResponse(
            id = request.id,
            name = request.name,
        ).also { audit.logCreate(it) }
    }

/** Логирует создание дисциплины: тип сущности `"discipline"`, данные — JSON-снапшот [result]. */
context(ctx: RequestContext)
fun AuditLog.logCreate(result: DisciplineDetailResponse) = logCreate("discipline", result.id, Json.encodeToString(result))
