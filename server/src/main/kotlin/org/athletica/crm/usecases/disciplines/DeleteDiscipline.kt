package org.athletica.crm.usecases.disciplines

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.right
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.disciplines.DeleteDisciplineRequest
import org.athletica.crm.api.schemas.disciplines.DisciplineDetailResponse
import org.athletica.crm.audit.AuditLog
import org.athletica.crm.audit.logDelete
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import kotlin.uuid.toJavaUuid

/**
 * Удаляет дисциплины по списку [DeleteDisciplineRequest.ids] одним запросом.
 * Один оператор DELETE атомичен сам по себе — транзакция не нужна.
 * Записи фильтруются по организации из [ctx], чужие id молча игнорируются.
 */
context(db: Database, ctx: RequestContext, audit: AuditLog)
suspend fun deleteDiscipline(request: DeleteDisciplineRequest): Either<CommonDomainError, Unit> =
    either {
        if (request.ids.isEmpty()) {
            return Unit.right()
        }
        val disciplines =
            disciplineList().bind()
                .filter { request.ids.contains(it.id) }

        db
            .sql("DELETE FROM disciplines WHERE id = ANY(:ids) AND org_id = :orgId")
            .bind("ids", request.ids.map { it.toJavaUuid() }.toTypedArray())
            .bind("orgId", ctx.orgId.value)
            .execute()

        disciplines.forEach {
            audit.logDelete(it)
        }
    }

/** Логирует удаление дисциплины: тип сущности `"discipline"`, данные — JSON-снапшот [result] после изменения. */
context(ctx: RequestContext)
fun AuditLog.logDelete(result: DisciplineDetailResponse) = logDelete("discipline", result.id, Json.encodeToString(result))
