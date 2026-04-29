package org.athletica.crm.usecases.auth

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.auth.AuthBranchesResponse
import org.athletica.crm.api.schemas.branches.BranchDetailResponse
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.branch.Branches
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean

/**
 * Возвращает список филиалов, доступных текущему аутентифицированному сотруднику.
 * Если у сотрудника [allBranchesAccess] = true — все филиалы организации.
 * Иначе — только явно назначенные через employee_branches.
 */
context(ctx: RequestContext, tr: Transaction, branches: Branches, raise: Raise<DomainError>)
suspend fun myBranches(): AuthBranchesResponse {
    val allBranchesAccess =
        tr
            .sql("SELECT all_branches_access FROM employees WHERE id = :id")
            .bind("id", ctx.employeeId)
            .firstOrNull { it.asBoolean("all_branches_access") } ?: true
    val accessible = branches.accessibleBranches(ctx.orgId, ctx.employeeId, allBranchesAccess)
    return AuthBranchesResponse(accessible.map { BranchDetailResponse(it.id, it.name) })
}
