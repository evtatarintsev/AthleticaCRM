package org.athletica.crm.usecases.auth

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.core.Lang
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.storage.Transaction

/** Зарегистрированный пользователь без данных о пароле. */
data class User(
    override val id: UserId,
    override val orgId: OrgId,
    override val branchId: BranchId,
    override val employeeId: EmployeeId,
    override val username: String,
) : AuthenticatedUser

/** Ошибки, возникающие при регистрации нового пользователя. */
sealed class SignUpError : DomainError {
    /** Логин уже занят другим пользователем. */
    data object UserAlreadyRegistered : SignUpError() {
        override val code = "USER_ALREADY_REGISTERED"
        override val message = "Пользователь с таким логином уже зарегистрирован"
    }
}

/**
 * Регистрирует новую организацию и её владельца по данным [request].
 * Создаёт филиал по умолчанию с именем, локализованным по [lang].
 * Возвращает созданного пользователя, либо [SignUpError.UserAlreadyRegistered] если логин занят.
 */
context(tr: Transaction, passwordHasher: PasswordHasher)
suspend fun signUp(
    request: SignUpRequest,
    lang: Lang = Lang.RU,
): Either<SignUpError, User> {
    val orgId = OrgId.new()
    val userId = UserId.new()
    val employeeId = EmployeeId.new()
    val branchId = BranchId.new()
    try {
        tr.sql("INSERT INTO organizations (id, name, timezone) VALUES (:orgId, :orgName, :timezone)")
            .bind("orgId", orgId)
            .bind("orgName", request.companyName)
            .bind("timezone", request.timezone)
            .execute()

        tr.sql("INSERT INTO users (id, login, password_hash) VALUES (:userId, :login, :hash)")
            .bind("userId", userId)
            .bind("login", request.login)
            .bind("hash", passwordHasher.hash(request.password).value)
            .execute()

        tr.sql("INSERT INTO employees (id, user_id, org_id, name, is_owner) VALUES (:employeeId, :userId, :orgId, :name, true)")
            .bind("employeeId", employeeId)
            .bind("orgId", orgId)
            .bind("userId", userId)
            .bind("name", request.userName)
            .execute()

        tr.sql("INSERT INTO branches (id, org_id, name) VALUES (:id, :orgId, :name)")
            .bind("id", branchId)
            .bind("orgId", orgId)
            .bind("name", Messages.DefaultBranchName.localize(lang))
            .execute()
    } catch (e: R2dbcDataIntegrityViolationException) {
        if (e.message?.contains("users_login_key") == true) {
            return SignUpError.UserAlreadyRegistered.left()
        }
        throw e
    }

    return User(id = userId, orgId = orgId, branchId = branchId, employeeId = employeeId, username = request.login).right()
}
