package org.athletica.crm.domain.clientbalance

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Money
import org.athletica.crm.domain.clients.Client
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asMoney
import org.athletica.crm.storage.asUuid

class DbClientBalances : ClientBalances {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun currentOf(client: Client): ClientBalance = currentOf(listOf(client)).single()

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun currentOf(clients: List<Client>): List<ClientBalance> {
        if (clients.isEmpty()) {
            return emptyList()
        }

        val balanceByClient =
            tr
                .sql(
                    """
                    SELECT DISTINCT ON (client_id) client_id, balance_after
                    FROM client_balance_journal
                    WHERE client_id = ANY(:clientIds) AND org_id = :orgId
                    ORDER BY client_id, created_at DESC
                    """.trimIndent(),
                )
                .bind("clientIds", clients.map { it.id.value })
                .bind("orgId", ctx.orgId)
                .list { row ->
                    row.asUuid("client_id").toClientId() to row.asMoney("balance_after", ctx.currency)
                }
                .toMap()

        return clients.map { client ->
            DbClientBalance(client.id, totalAmount = balanceByClient[client.id] ?: Money.zero(ctx.currency))
        }
    }
}
