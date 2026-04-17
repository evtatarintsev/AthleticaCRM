package org.athletica.crm.domain.clientbalance

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import org.athletica.crm.api.schemas.clients.PerformedBy
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logBalanceAdjust
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import kotlin.time.Clock
import kotlin.uuid.Uuid

class DbClientBalance(
    override val clientId: org.athletica.crm.core.ClientId,
    override val history: List<ClientBalanceEntry>,
    override val totalAmount: Double,
) : ClientBalance {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>, audit: AuditLog)
    override suspend fun adjust(amount: Double, note: String): ClientBalance {
        ensure(amount != 0.0) {
            CommonDomainError("BALANCE_AMOUNT_ZERO", Messages.BalanceAmountZero.localize())
        }
        ensure(note.isNotBlank()) {
            CommonDomainError("BALANCE_NOTE_REQUIRED", Messages.BalanceNoteRequired.localize())
        }

        val operationType = if (amount > 0) "admin_credit" else "admin_debit"
        val balanceAfter = totalAmount + amount

        // balance_after вычисляется прямо в INSERT подзапросом — атомарно на уровне БД,
        // без отдельного SELECT, который создавал бы race condition при конкурентных запросах.
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
            .bind("amount", java.math.BigDecimal(amount.toString()))
            .bind("operationType", operationType)
            .bind("note", note)
            .bind("performedBy", ctx.userId)
            .execute()

        audit.logBalanceAdjust(
            clientId = clientId,
            amount = amount,
            operationType = operationType,
            note = note,
        )

        val entry =
            ClientBalanceEntry(
                id = entryId,
                amount = amount,
                balanceAfter = balanceAfter,
                operationType = operationType,
                note = note,
                performedBy = PerformedBy(id = ctx.userId.value, name = ctx.username),
                createdAt = Clock.System.now(),
            )

        return DbClientBalance(clientId, listOf(entry) + history, balanceAfter)
    }
}
