package org.athletica.crm.domain.ledger

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.CashAccountId
import org.athletica.crm.core.entityids.CashArticleId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.LedgerAccountId
import org.athletica.crm.core.entityids.SaleId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.finance.LedgerKind
import org.athletica.crm.core.money.Money
import org.athletica.crm.i18n.LocalizationKey
import java.math.BigInteger

/** Владелец задаёт вид счёта и исключает невалидные сочетания аналитик. */
sealed interface LedgerOwner {
    /** Касса [id]. */
    data class Cash(val id: CashAccountId) : LedgerOwner

    /** Аванс клиента [clientId]; остаток кредитовый, то есть отрицательный. */
    data class Advance(val clientId: ClientId) : LedgerOwner

    /** Долг клиента [clientId] по продаже [saleId]. */
    data class Receivable(val saleId: SaleId, val clientId: ClientId) : LedgerOwner

    /** Прочий приход по статье [articleId]. */
    data class Income(val articleId: CashArticleId) : LedgerOwner

    /** Прочий расход по статье [articleId]. */
    data class Expense(val articleId: CashArticleId) : LedgerOwner

    /** Выручка от продаж организации. */
    data object Revenue : LedgerOwner

    /** Корректировки лицевых счетов клиентов. */
    data object Adjustment : LedgerOwner

    /** Начальные остатки касс. */
    data object Opening : LedgerOwner
}

/** Счёт с проверенным при загрузке владельцем. */
data class LedgerAccount internal constructor(val id: LedgerAccountId, val owner: LedgerOwner)

/** Одна ненулевая сумма по счёту; статья относится к движению по кассе. */
data class LedgerPosting(val account: LedgerAccount, val amount: Money, val articleId: CashArticleId? = null)

/** Проверенная двойная запись. Нулевые свёрнутые суммы удаляет шаблон операции до создания. */
class LedgerEntry private constructor(val kind: LedgerKind, val postings: List<LedgerPosting>) {
    companion object {
        /**
         * Запись вида [kind] из проводок [postings]: не меньше двух, ненулевые, в валюте организации,
         * с нулевой суммой; кассовые проводки, кроме перевода и начального остатка, — со статьёй.
         */
        context(ctx: RequestContext, raise: Raise<AppError>)
        fun of(kind: LedgerKind, postings: List<LedgerPosting>): LedgerEntry {
            ensure(kind != LedgerKind.REVERSAL && postings.size >= 2) { ledgerError("LEDGER_POSTINGS_REQUIRED") }
            ensure(postings.all { it.amount.currency == ctx.currency }) { ledgerError("LEDGER_CURRENCY_MISMATCH") }
            ensure(postings.none { it.amount.isZero }) { ledgerError("LEDGER_ZERO_POSTING") }
            ensure(postings.fold(BigInteger.ZERO) { sum, p -> sum + p.amount.minorUnits.toBigInteger() } == BigInteger.ZERO) {
                ledgerError("LEDGER_UNBALANCED")
            }
            ensure(postings.all { it.amount.minorUnits in -999999999999999999L..999999999999999999L }) { ledgerError("FINANCE_AMOUNT_TOO_LARGE") }
            ensure(postings.all { it.account.owner !is LedgerOwner.Cash || kind in setOf(LedgerKind.TRANSFER, LedgerKind.OPENING) || it.articleId != null }) {
                ledgerError("LEDGER_CASH_ARTICLE_REQUIRED")
            }
            return LedgerEntry(kind, postings.toList())
        }
    }
}

/** Сообщение общей валидации главной книги; код уточняет нарушенное правило. */
context(ctx: RequestContext)
internal fun ledgerError(code: String) = CommonDomainError(code, LedgerValidationMessage.localize())

private object LedgerValidationMessage : LocalizationKey {
    override val ru = "Операция нарушает правила главной книги"
    override val en = "The operation violates ledger rules"
}
