package org.athletica.crm.domain.clientbalance

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

interface ClientBalances {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun forClient(clientId: ClientId): ClientBalance
}
