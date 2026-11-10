package org.athletica.crm.domain.auth

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.storage.Transaction

class DbUsers(private val passwordHasher: PasswordHasher) : Users {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun new(login: String, password: String): User {
        val user = DbUser(UserId.new(), ctx.orgId, login)
        tr.sql("INSERT INTO users (id, login, password_hash) VALUES (:userId, :login, :hash)")
            .bind("userId", user.id)
            .bind("login", login)
            .bind("hash", passwordHasher.hash(password).value)
            .onViolation("users_login_key") { CommonDomainError("LOGIN_TAKEN", "Пользователь с таким email уже зарегистрирован") }
            .execute()
        return user
    }
}
