package org.athletica.crm.domain.clientbalance

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.money.Money
import org.athletica.crm.domain.clients.ActiveClient
import org.athletica.crm.domain.clients.Client
import org.athletica.crm.storage.Transaction

interface ClientBalances {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun adjust(client: ActiveClient, amount: Money, note: String): ClientBalance

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun currentOf(client: Client): ClientBalance

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun currentOf(clients: List<Client>): List<ClientBalance>
}
