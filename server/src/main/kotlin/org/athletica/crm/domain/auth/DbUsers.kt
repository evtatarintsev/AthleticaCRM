package org.athletica.crm.domain.auth

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.storage.Transaction

class DbUsers(private val passwordHasher: PasswordHasher) : Users {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(login: String, password: String): User {
        val user = DbUser(UserId.new(), ctx.orgId, login)
        try {
            tr.sql("INSERT INTO users (id, login, password_hash) VALUES (:userId, :login, :hash)")
                .bind("userId", user.id)
                .bind("login", login)
                .bind("hash", passwordHasher.hash(password).value)
                .execute()
        } catch (e: R2dbcDataIntegrityViolationException) {
            raise(CommonDomainError("LOGIN_TAKEN", "Пользователь с таким email уже зарегистрирован"))
        }
        return user
    }
}
