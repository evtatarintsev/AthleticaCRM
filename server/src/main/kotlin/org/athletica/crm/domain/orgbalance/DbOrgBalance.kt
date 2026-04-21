package org.athletica.crm.domain.orgbalance

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import org.athletica.crm.api.schemas.clients.PerformedBy
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import kotlin.time.Clock
import kotlin.uuid.Uuid

class DbOrgBalance(
    override val history: List<OrgBalanceEntry>,
    override val totalAmount: Double,
) : OrgBalance {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun adjust(amount: Double, description: String): OrgBalance {
        ensure(amount != 0.0) {
            CommonDomainError("BALANCE_AMOUNT_ZERO", Messages.BalanceAmountZero.localize())
        }

        val operationType = if (amount > 0) "admin_credit" else "admin_debit"
        val entryId = Uuid.generateV7()

        tr
            .sql(
                """
                INSERT INTO org_balance_journal
                    (id, org_id, amount, balance_after, operation_type, description, performed_by)
                VALUES (
                    :id, :orgId, :amount,
                    COALESCE((SELECT SUM(j.amount) FROM org_balance_journal j WHERE j.org_id = :orgId), 0) + :amount,
                    :operationType::org_balance_operation_type, :description, :performedBy
                )
                """.trimIndent(),
            )
            .bind("id", entryId)
            .bind("orgId", ctx.orgId)
            .bind("amount", java.math.BigDecimal(amount.toString()))
            .bind("operationType", operationType)
            .bind("description", description)
            .bind("performedBy", ctx.userId)
            .execute()

        val balanceAfter = totalAmount + amount
        val entry = OrgBalanceEntry(
            id = entryId,
            amount = amount,
            balanceAfter = balanceAfter,
            operationType = operationType,
            paymentMethod = null,
            description = description,
            performedBy = PerformedBy(id = ctx.userId.value, name = ctx.username),
            createdAt = Clock.System.now(),
        )

        return DbOrgBalance(listOf(entry) + history, balanceAfter)
    }
}
