package org.athletica.crm.domain.clients

import arrow.core.raise.context.Raise
import org.athletica.crm.core.ClientId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.db.Transaction

interface Clients {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byId(id: ClientId): Client
}
