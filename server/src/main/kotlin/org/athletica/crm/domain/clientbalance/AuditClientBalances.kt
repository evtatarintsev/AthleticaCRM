package org.athletica.crm.domain.clientbalance

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.storage.Transaction

class AuditClientBalances(private val delegate: ClientBalances, private val audit: AuditLog) : ClientBalances by delegate {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun forClient(clientId: ClientId) = AuditClientBalance(delegate.forClient(clientId), audit)
}
