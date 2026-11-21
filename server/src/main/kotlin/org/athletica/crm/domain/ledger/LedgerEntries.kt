package org.athletica.crm.domain.ledger

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.CashArticleId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.finance.LedgerKind
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.money.sum

/** Шаблоны всех денежных событий. Знак остатка аванса обратен видимой клиенту сумме. */
object LedgerEntries {
    /** Продажа на [amount]: растёт долг [receivable] и выручка [revenue]. */
    context(ctx: RequestContext, raise: Raise<AppError>)
    fun sale(receivable: LedgerAccount, revenue: LedgerAccount, amount: Money) = LedgerEntry.of(LedgerKind.SALE, listOf(LedgerPosting(receivable, amount), LedgerPosting(revenue, -amount)))

    /**
     * Оплата кассовыми частями [cash] и с аванса [fromAdvance] при долге [debt]: сначала гасится
     * долг [receivable], переплата зачисляется на аванс [advance].
     */
    context(ctx: RequestContext, raise: Raise<AppError>)
    fun payment(
        cash: List<LedgerPosting>,
        advance: LedgerAccount,
        receivable: LedgerAccount?,
        fromAdvance: Money,
        debt: Money,
    ): LedgerEntry {
        val total = cash.map { it.amount }.sum(ctx.currency) + fromAdvance
        val paid = minOf(total, debt)
        val advanceChange = fromAdvance - maxOf(total - debt, Money.zero(ctx.currency))
        val postings =
            cash +
                listOfNotNull(
                    receivable?.takeUnless { paid.isZero }?.let { LedgerPosting(it, -paid) },
                    advanceChange.takeUnless { it.isZero }?.let { LedgerPosting(advance, it) },
                )
        return LedgerEntry.of(LedgerKind.PAYMENT, postings)
    }

    /** Возврат [amount] при долге [debt]: уменьшает выручку, сначала гасит долг, остаток зачисляет на аванс. */
    context(ctx: RequestContext, raise: Raise<AppError>)
    fun saleReturn(
        revenue: LedgerAccount,
        receivable: LedgerAccount,
        advance: LedgerAccount,
        amount: Money,
        debt: Money,
    ): LedgerEntry {
        val debtReduction = minOf(amount, debt)
        val credit = amount - debtReduction
        return LedgerEntry.of(
            LedgerKind.SALE_RETURN,
            listOfNotNull(
                LedgerPosting(revenue, amount),
                debtReduction.takeUnless { it.isZero }?.let { LedgerPosting(receivable, -it) },
                credit.takeUnless { it.isZero }?.let { LedgerPosting(advance, -it) },
            ),
        )
    }

    /** Выплата [amount] клиенту с аванса [advance] из кассы [cash] по статье [articleId]. */
    context(ctx: RequestContext, raise: Raise<AppError>)
    fun payout(
        advance: LedgerAccount,
        cash: LedgerAccount,
        amount: Money,
        articleId: CashArticleId,
    ) = LedgerEntry.of(LedgerKind.PAYOUT, listOf(LedgerPosting(advance, amount), LedgerPosting(cash, -amount, articleId)))

    /** Корректировка аванса [advance] на [amount] против счёта корректировок; кассы не затрагивает. */
    context(ctx: RequestContext, raise: Raise<AppError>)
    fun adjustment(advance: LedgerAccount, adjustment: LedgerAccount, amount: Money) = LedgerEntry.of(LedgerKind.ADJUSTMENT, listOf(LedgerPosting(advance, -amount), LedgerPosting(adjustment, amount)))

    /** Кассовая операция вида [kind]: [amount] по кассе [cash] против счёта [offset] со статьёй [articleId]. */
    context(ctx: RequestContext, raise: Raise<AppError>)
    fun cash(
        kind: LedgerKind,
        cash: LedgerAccount,
        offset: LedgerAccount,
        amount: Money,
        articleId: CashArticleId? = null,
    ) = LedgerEntry.of(kind, listOf(LedgerPosting(cash, amount, articleId), LedgerPosting(offset, -amount)))
}
