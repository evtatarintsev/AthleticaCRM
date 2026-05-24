package org.athletica.crm.domain.orgbalance

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import org.athletica.crm.core.AdminRequestContext
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Money
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import kotlin.uuid.Uuid

/** Реализация [OrgBalance] с поддержкой записи через [adjust]. */
data class DbOrgBalance(
    override val totalAmount: Money,
    override val history: List<OrgBalanceEntry>,
) : OrgBalance {
    context(ctx: AdminRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun adjust(amount: Money, description: String): OrgBalance {
        ensure(amount.currency == ctx.currency) {
            CommonDomainError("BALANCE_CURRENCY_MISMATCH", Messages.BalanceCurrencyMismatch.localize())
        }
        ensure(!amount.isZero) {
            CommonDomainError("BALANCE_AMOUNT_ZERO", Messages.BalanceAmountZero.localize())
        }
        ensure(description.isNotBlank()) {
            CommonDomainError("BALANCE_DESCRIPTION_REQUIRED", Messages.BalanceNoteRequired.localize())
        }

        val operationType = if (amount.isPositive) "admin_credit" else "admin_debit"
        val balanceAfter = totalAmount + amount

        tr
            .sql(
                """
                INSERT INTO org_balance_journal
                    (id, org_id, amount, balance_after, operation_type, description)
                VALUES (
                    :id, :orgId, :amount,
                    COALESCE((SELECT SUM(j.amount) FROM org_balance_journal j WHERE j.org_id = :orgId), 0) + :amount,
                    :operationType::org_balance_operation_type, :description
                )
                """.trimIndent(),
            )
            .bind("id", Uuid.generateV7())
            .bind("orgId", ctx.orgId)
            .bind("amount", amount)
            .bind("operationType", operationType)
            .bind("description", description)
            .execute()

        return DbOrgBalance(balanceAfter, history)
    }
}
