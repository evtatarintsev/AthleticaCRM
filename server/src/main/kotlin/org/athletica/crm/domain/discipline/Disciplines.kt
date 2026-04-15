package org.athletica.crm.domain.discipline

import arrow.core.raise.context.Raise
import kotlinx.serialization.Serializable
import org.athletica.crm.core.DisciplineId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.DomainError

interface Disciplines {
    context(ctx: RequestContext, raise: Raise<DomainError>)
    suspend fun list(): List<Discipline>

    context(ctx: RequestContext, raise: Raise<DomainError>)
    suspend fun create(discipline: Discipline)

    context(ctx: RequestContext, raise: Raise<DomainError>)
    suspend fun update(discipline: Discipline)

    context(ctx: RequestContext, raise: Raise<DomainError>)
    suspend fun delete(ids: List<DisciplineId>)
}

@Serializable
data class Discipline(
    val id: DisciplineId,
    val name: String,
)
