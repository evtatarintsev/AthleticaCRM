package org.athletica.crm.usecases.disciplines

import arrow.core.Either
import arrow.core.raise.either
import org.athletica.crm.api.schemas.disciplines.DeleteDisciplineRequest
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import kotlin.uuid.toJavaUuid

/**
 * Удаляет дисциплины по списку [DeleteDisciplineRequest.ids] одним запросом.
 * Один оператор DELETE атомичен сам по себе — транзакция не нужна.
 * Записи фильтруются по организации из [ctx], чужие id молча игнорируются.
 */
context(db: Database, ctx: RequestContext)
suspend fun deleteDiscipline(request: DeleteDisciplineRequest): Either<CommonDomainError, Unit> =
    either {
        if (request.ids.isEmpty()) return@either

        db
            .sql("DELETE FROM disciplines WHERE id = ANY(:ids) AND org_id = :orgId")
            .bind("ids", request.ids.map { it.toJavaUuid() }.toTypedArray())
            .bind("orgId", ctx.orgId.value)
            .execute()
    }
