package org.athletica.crm.domain.auth

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.NonBlankName
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.branch.Branch
import org.athletica.crm.storage.Transaction

interface Auth {
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun login(
        username: String,
        password: String,
        branchId: BranchId,
        lang: Lang,
        clientIp: String?,
    ): AuthenticatedUser

    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun signup(request: SignUp, lang: Lang, clientIp: String?): AuthenticatedUser

    /** Завершает сессию, в которой выполнен запрос. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun logout()

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun updateMe(name: NonBlankName, avatarId: UploadId?)

    /** Меняет пароль [oldPassword] на [newPassword] и завершает все другие сессии пользователя. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun changePassword(oldPassword: String, newPassword: String)

    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun availableBranches(
        username: String,
        password: String,
        lang: Lang,
        clientIp: String?,
    ): List<Branch>

    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun userById(id: UserId, branchId: BranchId): AuthenticatedUser

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun switchBranch(branchId: BranchId): AuthenticatedUser
}

/** Данные регистрации новой организации и её владельца. */
data class SignUp(
    /** Название организации. */
    val companyName: NonBlankName,
    /** Имя владельца. */
    val userName: NonBlankName,
    /** Логин владельца — адрес электронной почты. */
    val login: String,
    /** Пароль владельца в открытом виде. */
    val password: String,
    /** Часовой пояс организации. */
    val timezone: String,
    /** Валюта организации; фиксируется на регистрации. */
    val currency: Currency,
)
