package org.athletica.crm.domain.clientbalance

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.money.Money
import org.athletica.crm.domain.clients.ActiveClient
import org.athletica.crm.domain.clients.Client
import org.athletica.crm.domain.clients.lockForWrite
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asMoney
import org.athletica.crm.storage.asUuid
import kotlin.uuid.Uuid

class DbClientBalances : ClientBalances {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun adjust(client: ActiveClient, amount: Money, note: String): ClientBalance {
        ensure(amount.currency == ctx.currency) {
            CommonDomainError("BALANCE_CURRENCY_MISMATCH", Messages.BalanceCurrencyMismatch.localize())
        }
        ensure(!amount.isZero) {
            CommonDomainError("BALANCE_AMOUNT_ZERO", Messages.BalanceAmountZero.localize())
        }
        ensure(note.isNotBlank()) {
            CommonDomainError("BALANCE_NOTE_REQUIRED", Messages.BalanceNoteRequired.localize())
        }

        val operationType = if (amount.isPositive) "admin_credit" else "admin_debit"
        client.lockForWrite()
        val balanceAfter =
            checkNotNull(
                tr
                    .sql(
                        """
                        INSERT INTO client_balance_journal
                            (id, org_id, client_id, amount, balance_after, operation_type, note, performed_by)
                        VALUES (
                            :id, :orgId, :clientId, :amount,
                            COALESCE((SELECT SUM(j.amount) FROM client_balance_journal j WHERE j.client_id = :clientId), 0) + :amount,
                            :operationType::balance_operation_type, :note, :performedBy
                        ) RETURNING balance_after
                        """.trimIndent(),
                    )
                    .bind("id", Uuid.generateV7())
                    .bind("orgId", ctx.orgId)
                    .bind("clientId", client.id)
                    .bind("amount", amount)
                    .bind("operationType", operationType)
                    .bind("note", note)
                    .bind("performedBy", ctx.employee.id)
                    .firstOrNull { it.asMoney("balance_after", ctx.currency) },
            )

        return DbClientBalance(client.id, balanceAfter)
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun currentOf(client: Client): ClientBalance = currentOf(listOf(client)).single()

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
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
