package org.athletica.crm.domain.branch

import arrow.core.raise.context.Raise
import kotlinx.serialization.Serializable
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

interface Branches {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(): List<Branch>

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun create(branch: Branch)

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun update(branch: Branch)

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun delete(ids: List<BranchId>)
}

@Serializable
data class Branch(
    val id: BranchId,
    val name: String,
)
