package org.athletica.crm.domain.hall

import arrow.core.raise.context.Raise
import kotlinx.serialization.Serializable
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

interface Halls {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(): List<Hall>

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun create(hall: Hall)

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun update(hall: Hall)

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun delete(ids: List<HallId>)

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byId(id: HallId): Hall
}

@Serializable
data class Hall(
    val id: HallId,
    val name: String,
)
