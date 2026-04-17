package org.athletica.crm.domain.auth

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

interface Users {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun new(login: String, password: String): User
}
