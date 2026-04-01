package org.athletica.crm.usecases.sports

import arrow.core.Either
import arrow.core.raise.either
import org.athletica.crm.api.schemas.sports.DeleteSportRequest
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import kotlin.uuid.toJavaUuid

/**
 * Удаляет виды спорта по списку [DeleteSportRequest.ids] одним запросом.
 * Один оператор DELETE атомичен сам по себе — транзакция не нужна.
 * Записи фильтруются по организации из [ctx], чужие id молча игнорируются.
 */
context(db: Database, ctx: RequestContext)
suspend fun deleteSport(request: DeleteSportRequest): Either<CommonDomainError, Unit> =
    either {
        if (request.ids.isEmpty()) return@either

        db
            .sql("DELETE FROM sports WHERE id = ANY(:ids) AND org_id = :orgId")
            .bind("ids", request.ids.map { it.toJavaUuid() }.toTypedArray())
            .bind("orgId", ctx.orgId.value)
            .execute()
    }
