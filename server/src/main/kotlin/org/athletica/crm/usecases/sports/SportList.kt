package org.athletica.crm.usecases.sports

import arrow.core.Either
import arrow.core.right
import org.athletica.crm.api.schemas.sports.SportDetailResponse
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import kotlin.uuid.toKotlinUuid

/**
 * Возвращает список всех видов спорта организации из [ctx], отсортированных по названию.
 */
context(db: Database, ctx: RequestContext)
suspend fun sportList(): Either<CommonDomainError, List<SportDetailResponse>> =
    db
        .sql(
            """
            SELECT s.id, s.name
            FROM sports s
            WHERE s.org_id = :orgId
            ORDER BY s.name
            """.trimIndent(),
        )
        .bind("orgId", ctx.orgId.value)
        .list { row, _ ->
            SportDetailResponse(
                id = row.get("id", java.util.UUID::class.java)!!.toKotlinUuid(),
                name = row.get("name", String::class.java)!!,
            )
        }
        .right()
