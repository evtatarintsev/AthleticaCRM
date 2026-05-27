package org.athletica.crm.usecases.auth

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.security.User
import org.athletica.crm.security.userById
import org.athletica.crm.storage.Transaction

/**
 * Переключает текущего пользователя на другой филиал.
 *
 * Проверяет, что сотрудник имеет доступ к [branchId] через
 * [EmployeeRequestContext.availableBranches], затем возвращает нового [User] с
 * обновлённым [branchId] для выпуска нового JWT-токена.
 * Если доступ запрещён — возвращает [DomainError] с кодом `BRANCH_ACCESS_DENIED`.
 */
context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun switchBranch(branchId: BranchId): User {
    if (!ctx.availableBranches.contains(branchId)) {
        raise(CommonDomainError("BRANCH_ACCESS_DENIED", "Access to branch denied"))
    }
    return userById(ctx.userId, branchId)
}
