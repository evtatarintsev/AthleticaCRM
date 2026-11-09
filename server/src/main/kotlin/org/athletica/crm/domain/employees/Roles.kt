package org.athletica.crm.domain.employees

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.storage.Transaction
import kotlin.uuid.Uuid

interface Roles {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun new(role: EmployeeRole)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun update(role: EmployeeRole)

    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun list(): List<EmployeeRole>
}

data class EmployeeRole(
    val id: Uuid,
    val name: String,
    val permissions: Set<UserPermission>,
)
