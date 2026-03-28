package org.athletica.crm.usecases

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.db.Database
import org.athletica.crm.security.PasswordHasher
import kotlin.uuid.Uuid

/** Зарегистрированный пользователь без данных о пароле. */
data class User(
    override val id: Uuid,
    override val orgId: Uuid,
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
 * Возвращает созданного пользователя, либо [SignUpError.UserAlreadyRegistered] если логин занят.
 */
context(db: Database, passwordHasher: PasswordHasher, ctx: RequestContext)
suspend fun signUp(request: SignUpRequest): Either<SignUpError, User> {
    val orgId = Uuid.generateV7()
    val userId = Uuid.generateV7()
    try {
        db.transaction {
            sql("INSERT INTO organizations (id, name) VALUES (:orgId, :orgName)")
                .bind("orgId", orgId)
                .bind("orgName", request.companyName)
                .execute()

            sql("INSERT INTO users (id, login, name, password_hash) VALUES (:userId, :login, :userName, :hash)")
                .bind("userId", userId)
                .bind("login", request.login)
                .bind("userName", request.userName)
                .bind("hash", passwordHasher.hash(request.password).value)
                .execute()

            sql("INSERT INTO employees (user_id, org_id, is_owner) VALUES (:userId, :orgId, true)")
                .bind("orgId", orgId)
                .bind("userId", userId)
                .execute()
        }
    } catch (e: R2dbcDataIntegrityViolationException) {
        if (e.message?.contains("users_login_key") == true) {
            return SignUpError.UserAlreadyRegistered.left()
        }
        throw e
    }

    return User(id = userId, orgId = orgId, username = request.login).right()
}
