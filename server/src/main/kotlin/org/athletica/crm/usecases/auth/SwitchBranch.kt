package org.athletica.crm.usecases.auth

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.branch.Branches
import org.athletica.crm.security.User
import org.athletica.crm.security.userById
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean

/**
 * Переключает текущего пользователя на другой филиал.
 *
 * Проверяет, что сотрудник имеет доступ к [branchId], затем возвращает
 * нового [User] с обновлённым [branchId] для выпуска нового JWT-токена.
 * Если доступ запрещён — возвращает [DomainError] с кодом `BRANCH_ACCESS_DENIED`.
 */
context(ctx: RequestContext, tr: Transaction, branches: Branches, raise: Raise<DomainError>)
suspend fun switchBranch(branchId: BranchId): User {
    val allBranchesAccess =
        tr.sql("SELECT all_branches_access FROM employees WHERE id = :id")
            .bind("id", ctx.employeeId)
            .firstOrNull { it.asBoolean("all_branches_access") } ?: true

    val accessible = branches.accessibleBranches(ctx.orgId, ctx.employeeId, allBranchesAccess)
    if (accessible.none { it.id == branchId }) {
        raise(CommonDomainError("BRANCH_ACCESS_DENIED", "Access to branch denied"))
    }

    return userById(ctx.userId, branchId)
}
