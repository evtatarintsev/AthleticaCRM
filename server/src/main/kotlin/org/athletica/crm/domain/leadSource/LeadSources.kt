package org.athletica.crm.domain.leadSource

import arrow.core.raise.context.Raise
import kotlinx.serialization.Serializable
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.LeadSourceId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

interface LeadSources {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(): List<LeadSource>

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun create(leadSource: LeadSource)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun update(leadSource: LeadSource)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun delete(ids: List<LeadSourceId>)
}

@Serializable
data class LeadSource(
    val id: LeadSourceId,
    val name: String,
)
