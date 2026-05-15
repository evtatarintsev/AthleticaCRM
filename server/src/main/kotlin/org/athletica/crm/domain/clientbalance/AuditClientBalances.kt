package org.athletica.crm.domain.clientbalance

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.clients.Client
import org.athletica.crm.storage.Transaction

class AuditClientBalances(private val delegate: ClientBalances, private val audit: AuditLog) : ClientBalances by delegate {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun currentOf(client: Client) = AuditClientBalance(delegate.currentOf(client), audit)

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun currentOf(clients: List<Client>) = clients.map { AuditClientBalance(delegate.currentOf(it), audit) }
}
