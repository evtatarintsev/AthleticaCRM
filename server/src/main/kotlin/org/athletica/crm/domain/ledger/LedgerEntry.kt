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
    data class Cash(val id: CashAccountId) : LedgerOwner

    data class Advance(val clientId: ClientId) : LedgerOwner

    data class Receivable(val saleId: SaleId, val clientId: ClientId) : LedgerOwner

    data class Income(val articleId: CashArticleId) : LedgerOwner

    data class Expense(val articleId: CashArticleId) : LedgerOwner

    data object Revenue : LedgerOwner

    data object Adjustment : LedgerOwner

    data object Opening : LedgerOwner
}

/** Счёт с проверенным при загрузке владельцем. */
data class LedgerAccount internal constructor(val id: LedgerAccountId, val owner: LedgerOwner)

/** Одна ненулевая сумма по счёту; статья относится к движению по кассе. */
data class LedgerPosting(val account: LedgerAccount, val amount: Money, val articleId: CashArticleId? = null)

/** Проверенная двойная запись. Нулевые свёрнутые суммы удаляет шаблон операции до создания. */
class LedgerEntry private constructor(val kind: LedgerKind, val postings: List<LedgerPosting>) {
    companion object {
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
