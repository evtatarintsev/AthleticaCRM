package org.athletica.crm.domain.ledger

import arrow.core.Either
import arrow.core.raise.context.either
import org.athletica.crm.core.entityids.CashAccountId
import org.athletica.crm.core.entityids.CashArticleId
import org.athletica.crm.core.entityids.LedgerAccountId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.finance.LedgerKind
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.athletica.crm.employeeContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LedgerEntryTest {
    private val ctx = employeeContext(OrgId.new())
    private val revenue = LedgerAccount(LedgerAccountId.new(), LedgerOwner.Revenue)
    private val opening = LedgerAccount(LedgerAccountId.new(), LedgerOwner.Opening)
    private val cash = LedgerAccount(LedgerAccountId.new(), LedgerOwner.Cash(CashAccountId.new()))

    private fun posting(amount: Long, currency: Currency = Currency.RUB) = LedgerPosting(revenue, Money(amount, currency))

    private fun entry(postings: List<LedgerPosting>, kind: LedgerKind = LedgerKind.SALE): Either<AppError, LedgerEntry> = context(ctx) { either { LedgerEntry.of(kind, postings) } }

    @Test
    fun `сбалансированная запись допустима`() {
        assertIs<Either.Right<LedgerEntry>>(entry(listOf(posting(450000), posting(-450000))))
    }

    @Test
    fun `каждое нарушение отклоняется отдельно`() {
        val cases =
            listOf(
                listOf(posting(1)) to "LEDGER_POSTINGS_REQUIRED",
                listOf(posting(0), posting(0)) to "LEDGER_ZERO_POSTING",
                listOf(posting(10), posting(-9)) to "LEDGER_UNBALANCED",
                listOf(posting(10), posting(-10, Currency.USD)) to "LEDGER_CURRENCY_MISMATCH",
                listOf(LedgerPosting(cash, Money(10, Currency.RUB)), posting(-10)) to "LEDGER_CASH_ARTICLE_REQUIRED",
                listOf(posting(Long.MIN_VALUE), posting(Long.MIN_VALUE)) to "LEDGER_UNBALANCED",
            )
        cases.forEach { (postings, code) -> assertEquals(code, assertIs<Either.Left<AppError>>(entry(postings)).value.code) }
    }

    @Test
    fun `касса требует статью кроме перевода и ввода остатков`() {
        val postings = listOf(LedgerPosting(cash, Money(10, Currency.RUB)), LedgerPosting(opening, Money(-10, Currency.RUB)))
        assertIs<Either.Right<LedgerEntry>>(entry(postings, LedgerKind.OPENING))
        assertIs<Either.Right<LedgerEntry>>(entry(postings, LedgerKind.TRANSFER))
        assertIs<Either.Right<LedgerEntry>>(entry(listOf(postings[0].copy(articleId = CashArticleId.new()), postings[1]), LedgerKind.INCOME))
    }
}
