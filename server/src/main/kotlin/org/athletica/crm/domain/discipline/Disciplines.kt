package org.athletica.crm.domain.discipline

import arrow.core.Either
import kotlinx.serialization.Serializable
import org.athletica.crm.core.DisciplineId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError

interface Disciplines {
    context(ctx: RequestContext)
    suspend fun list(): Either<CommonDomainError, List<Discipline>>

    context(ctx: RequestContext)
    suspend fun create(discipline: Discipline): Either<CommonDomainError, Unit>

    context(ctx: RequestContext)
    suspend fun update(discipline: Discipline): Either<CommonDomainError, Unit>

    context(ctx: RequestContext)
    suspend fun delete(ids: List<DisciplineId>): Either<CommonDomainError, Unit>
}

@Serializable
data class Discipline(
    val id: DisciplineId,
    val name: String,
)
