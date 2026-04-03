package org.athletica.crm.usecases.disciplines

import arrow.core.Either
import arrow.core.right
import org.athletica.crm.api.schemas.disciplines.DisciplineDetailResponse
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import kotlin.uuid.toKotlinUuid

/**
 * Возвращает список всех дисциплин организации из [ctx], отсортированных по названию.
 */
context(db: Database, ctx: RequestContext)
suspend fun disciplineList(): Either<CommonDomainError, List<DisciplineDetailResponse>> =
    db
        .sql(
            """
            SELECT s.id, s.name
            FROM disciplines s
            WHERE s.org_id = :orgId
            ORDER BY s.name
            """.trimIndent(),
        )
        .bind("orgId", ctx.orgId.value)
        .list { row, _ ->
            DisciplineDetailResponse(
                id = row.get("id", java.util.UUID::class.java)!!.toKotlinUuid(),
                name = row.get("name", String::class.java)!!,
            )
        }
        .right()
