package org.athletica.crm.usecases.sports

import arrow.core.Either
import arrow.core.raise.either
import org.athletica.crm.api.schemas.sports.DeleteSportRequest
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database

/**
 * Удаляет виды спорта по списку [DeleteSportRequest.ids].
 * Операция атомарна — удаляет либо все, либо ни одного.
 * Записи фильтруются по организации из [ctx], чужие id молча игнорируются.
 */
context(db: Database, ctx: RequestContext)
suspend fun deleteSport(request: DeleteSportRequest): Either<CommonDomainError, Unit> =
    either {
        if (request.ids.isEmpty()) return@either

        db.transaction {
            request.ids.forEach { id ->
                sql("DELETE FROM sports WHERE id = :id AND org_id = :orgId")
                    .bind("id", id)
                    .bind("orgId", ctx.orgId.value)
                    .execute()
            }
        }
    }
