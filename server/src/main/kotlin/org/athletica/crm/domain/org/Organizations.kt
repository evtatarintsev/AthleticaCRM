package org.athletica.crm.domain.org

import arrow.core.raise.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

interface Organizations {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun current(): Organization
}

interface Organization {
    val id: OrgId
    val name: String
    val timezone: String

    context(ctx: RequestContext, raise: Raise<DomainError>)
    suspend fun withNew(newName: String, newTimezone: String): Organization

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun save()
}
