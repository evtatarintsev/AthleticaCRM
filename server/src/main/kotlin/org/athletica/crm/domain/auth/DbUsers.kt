package org.athletica.crm.domain.auth

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.storage.Transaction

class DbUsers(private val passwordHasher: PasswordHasher) : Users {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(login: String, password: String): User {
        val user =
            DbUser(
                UserId.new(),
                ctx.orgId,
                login,
            )
        tr.sql("INSERT INTO users (id, login, password_hash) VALUES (:userId, :login, :hash)")
            .bind("userId", user.id)
            .bind("login", login)
            .bind("hash", passwordHasher.hash(password).value)
            .execute()
        return user
    }
}
