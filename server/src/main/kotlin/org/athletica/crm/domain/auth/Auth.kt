package org.athletica.crm.domain.auth

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.branch.Branch
import org.athletica.crm.storage.Transaction

interface Auth {
    context(tr: Transaction, raise: Raise<DomainError>)
    suspend fun login(
        username: String,
        password: String,
        branchId: BranchId,
        lang: Lang,
        clientIp: String?,
    ): AuthenticatedUser

    context(tr: Transaction, raise: Raise<DomainError>)
    suspend fun signup(request: SignUpRequest, lang: Lang, clientIp: String?): AuthenticatedUser

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun logout()

    context(tr: Transaction, raise: Raise<DomainError>)
    suspend fun availableBranches(
        username: String,
        password: String,
        lang: Lang,
        clientIp: String?,
    ): List<Branch>

    context(tr: Transaction, raise: Raise<DomainError>)
    suspend fun userById(id: UserId, branchId: BranchId): AuthenticatedUser

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun switchBranch(branchId: BranchId): AuthenticatedUser
}
