package org.athletica.crm.domain.employees

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.permissions.Permission
import org.athletica.crm.storage.Transaction
import kotlin.uuid.Uuid

interface Roles {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun new(role: EmployeeRole)

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(): List<EmployeeRole>
}

data class EmployeeRole(
    val id: Uuid,
    val name: String,
    val permissions: Set<Permission>,
)
