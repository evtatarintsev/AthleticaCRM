package org.athletica.crm.domain.ledger

import arrow.core.Either
import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.SystemRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.LedgerAccountId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.core.finance.LedgerKind
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.athletica.crm.domain.org.OrgCalendar
import org.athletica.crm.storage.Transaction
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LedgerTest {
    private val org = OrgId.new()
    private val client = ClientId.new()
    private val ctx = SystemRequestContext(orgId = org, currency = Currency.RUB)
    private val today = LocalDate(2026, 11, 20)
    private val ledger =
        Ledger(
            object : OrgCalendar {
                context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
                override suspend fun today() = today
            },
        )

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    context(raise: Raise<AppError>)
    private suspend fun fixture() {
        TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, 'Ledger')").bind("id", org).execute()
        TestPostgres.db.sql("INSERT INTO clients (id, org_id, name, gender) VALUES (:id, :org, 'Client', 'MALE')").bind("id", client).bind("org", org).execute()
    }

    @Test
    fun `один счёт аванса на клиента`() =
        runTest {
            orThrow {
                fixture()
                TestPostgres.db.transaction {
                    context(ctx) {
                        assertEquals(ledger.account(LedgerOwner.Advance(client)), ledger.account(LedgerOwner.Advance(client)))
                    }
                }
            }
        }

    @Test
    fun `остаток на дату включает только прошедшие проводки`() =
        runTest {
            orThrow {
                fixture()
                TestPostgres.db.transaction {
                    context(ctx) {
                        val advance = ledger.account(LedgerOwner.Advance(client))
                        val adjustment = ledger.account(LedgerOwner.Adjustment)
                        ledger.record(LedgerEntry.of(LedgerKind.ADJUSTMENT, listOf(LedgerPosting(advance, Money(-10000, Currency.RUB)), LedgerPosting(adjustment, Money(10000, Currency.RUB)))), LocalDate(2026, 11, 1))
                        ledger.record(LedgerEntry.of(LedgerKind.ADJUSTMENT, listOf(LedgerPosting(advance, Money(3000, Currency.RUB)), LedgerPosting(adjustment, Money(-3000, Currency.RUB)))), LocalDate(2026, 11, 5))
                        assertEquals(Money(-10000, Currency.RUB), ledger.balance(advance, LocalDate(2026, 11, 3)))
                        assertEquals(Money(-7000, Currency.RUB), ledger.balance(advance, LocalDate(2026, 11, 5)))
                    }
                }
            }
        }

    @Test
    fun `счёт чужой организации отклоняется`() =
        runTest {
            orThrow { fixture() }
            val otherClient = ClientId.new()
            val result =
                either {
                    TestPostgres.db.transaction { context(ctx) { ledger.account(LedgerOwner.Advance(otherClient)) } }
                }
            assertEquals("LEDGER_ACCOUNT_NOT_FOUND", assertIs<Either.Left<AppError>>(result).value.code)
            val forged = LedgerAccount(LedgerAccountId.new(), LedgerOwner.Revenue)
            val balance = either { TestPostgres.db.transaction { context(ctx) { ledger.balance(forged) } } }
            assertEquals("LEDGER_ACCOUNT_NOT_FOUND", assertIs<Either.Left<AppError>>(balance).value.code)
        }

    @Test
    fun `сторно датируется сегодня и не меняет исторический остаток`() =
        runTest {
            orThrow {
                fixture()
                TestPostgres.db.transaction {
                    context(ctx) {
                        val advance = ledger.account(LedgerOwner.Advance(client))
                        val adjustment = ledger.account(LedgerOwner.Adjustment)
                        val id = ledger.record(LedgerEntry.of(LedgerKind.ADJUSTMENT, listOf(LedgerPosting(advance, Money(-100, Currency.RUB)), LedgerPosting(adjustment, Money(100, Currency.RUB)))), LocalDate(2026, 11, 1))
                        val reversal = ledger.reverse(id, "Ошибка")
                        assertEquals(Money(-100, Currency.RUB), ledger.balance(advance, LocalDate(2026, 11, 19)))
                        assertEquals(Money.zero(Currency.RUB), ledger.balance(advance, today))
                        val repeated = either { ledger.reverse(id, "Повтор") }
                        assertEquals("LEDGER_ALREADY_REVERSED", assertIs<Either.Left<AppError>>(repeated).value.code)
                        val reverseReversal = either { ledger.reverse(reversal, "Ошибка") }
                        assertEquals("LEDGER_REVERSE_REVERSAL", assertIs<Either.Left<AppError>>(reverseReversal).value.code)
                    }
                }
            }
        }

    @Test
    fun `будущая дата отклоняется без записи`() =
        runTest {
            orThrow { fixture() }
            val result =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) {
                            val advance = ledger.account(LedgerOwner.Advance(client))
                            val adjustment = ledger.account(LedgerOwner.Adjustment)
                            ledger.record(LedgerEntry.of(LedgerKind.ADJUSTMENT, listOf(LedgerPosting(advance, Money(-100, Currency.RUB)), LedgerPosting(adjustment, Money(100, Currency.RUB)))), LocalDate(2026, 11, 21))
                        }
                    }
                }
            assertEquals("FINANCE_FUTURE_DATE", assertIs<Either.Left<AppError>>(result).value.code)
        }
}
