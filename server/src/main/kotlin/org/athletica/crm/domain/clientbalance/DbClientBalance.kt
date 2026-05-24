package org.athletica.crm.domain.clientbalance

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Money
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asMoney
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid
import kotlin.uuid.Uuid

class DbClientBalance(
    override val clientId: ClientId,
    override val totalAmount: Money,
) : ClientBalance {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun adjust(amount: Money, note: String): ClientBalance {
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
        val balanceAfter = totalAmount + amount

        val entryId = Uuid.generateV7()
        tr
            .sql(
                """
                INSERT INTO client_balance_journal
                    (id, org_id, client_id, amount, balance_after, operation_type, note, performed_by)
                VALUES (
                    :id, :orgId, :clientId, :amount,
                    COALESCE((SELECT SUM(j.amount) FROM client_balance_journal j WHERE j.client_id = :clientId), 0) + :amount,
                    :operationType::balance_operation_type, :note, :performedBy
                )
                """.trimIndent(),
            )
            .bind("id", entryId)
            .bind("orgId", ctx.orgId)
            .bind("clientId", clientId)
            .bind("amount", amount)
            .bind("operationType", operationType)
            .bind("note", note)
            .bind("performedBy", ctx.employeeId)
            .execute()

        return DbClientBalance(clientId, balanceAfter)
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun history(): List<ClientBalanceEntry> =
        tr
            .sql(
                """
                SELECT j.id,
                       j.amount,
                       j.balance_after,
                       j.operation_type,
                       j.note,
                       j.created_at,
                       j.performed_by
                FROM client_balance_journal j
                WHERE j.client_id = :clientId AND j.org_id = :orgId
                ORDER BY j.created_at DESC
                """.trimIndent(),
            )
            .bind("clientId", clientId)
            .bind("orgId", ctx.orgId)
            .list { row ->
                ClientBalanceEntry(
                    id = row.asUuid("id"),
                    amount = row.asMoney("amount", ctx.currency),
                    balanceAfter = row.asMoney("balance_after", ctx.currency),
                    operationType = row.asString("operation_type"),
                    note = row.asStringOrNull("note"),
                    performedBy = EmployeeId(row.asUuid("performed_by")),
                    createdAt = row.asInstant("created_at"),
                )
            }
}
