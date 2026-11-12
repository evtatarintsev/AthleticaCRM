package org.athletica.crm.usecases.payments

import arrow.core.Either
import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.athletica.crm.domain.orgbalance.DbOrgBalances
import org.athletica.crm.domain.payment.DbPayments
import org.athletica.crm.domain.payment.PaymentAlreadyProcessed
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Тесты сценария [confirmPayment]: атомарность подтверждения и зачисления, повторное уведомление. */
class ConfirmPaymentTest {
    private val payments = DbPayments()
    private val orgBalances = DbOrgBalances()

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    @Test
    fun `успешная оплата отмечает платёж и зачисляет сумму`() =
        runTest {
            val ctx = paymentTestContext()
            insertPendingPayment(ctx, "ext_ok", Money(1_000_00, Currency.RUB))

            val result = either { confirm("ext_ok") }

            assertIs<Either.Right<Unit>>(result)
            assertEquals("paid", paymentStatus("ext_ok"))
            assertEquals(1, orgBalanceEntryCount(ctx.orgId))
            val balance = orThrow { TestPostgres.db.transaction { context(ctx) { orgBalances.current() } } }
            assertEquals(Money(1_000_00, Currency.RUB), balance.totalAmount)
        }

    @Test
    fun `отказ зачисления откатывает отметку оплаты`() =
        runTest {
            val ctx = paymentTestContext()
            insertPendingPayment(ctx, "ext_usd", Money(1_000_00, Currency.USD))

            val result = either { confirm("ext_usd") }

            val error = assertIs<Either.Left<AppError>>(result).value
            assertEquals("BALANCE_CURRENCY_MISMATCH", error.code)
            assertEquals("pending", paymentStatus("ext_usd"))
            assertEquals(0, orgBalanceEntryCount(ctx.orgId))
        }

    @Test
    fun `повторное уведомление не зачисляет второй раз`() =
        runTest {
            val ctx = paymentTestContext()
            insertPendingPayment(ctx, "ext_twice", Money(500_00, Currency.RUB))

            assertIs<Either.Right<Unit>>(either { confirm("ext_twice") })
            val second = either { confirm("ext_twice") }

            assertIs<Either.Left<PaymentAlreadyProcessed>>(second)
            assertEquals("paid", paymentStatus("ext_twice"))
            assertEquals(1, orgBalanceEntryCount(ctx.orgId))
        }

    @Test
    fun `неизвестный платёж ничего не меняет`() =
        runTest {
            val ctx = paymentTestContext()

            val result = either { confirm("ext_unknown") }

            assertIs<Either.Left<PaymentAlreadyProcessed>>(result)
            assertEquals(0, orgBalanceEntryCount(ctx.orgId))
        }

    context(raise: Raise<AppError>)
    private suspend fun confirm(externalPaymentId: String) = confirmPayment(TestPostgres.db, payments, orgBalances, "yookassa", externalPaymentId)

    private suspend fun insertPendingPayment(
        ctx: EmployeeRequestContext,
        externalPaymentId: String,
        amount: Money,
    ) {
        orThrow {
            TestPostgres.db.transaction {
                context(ctx) {
                    payments.create(
                        gatewayName = "yookassa",
                        externalPaymentId = externalPaymentId,
                        amount = amount,
                        description = "Пополнение",
                        confirmationUrl = "https://yookassa.ru/pay/$externalPaymentId",
                    )
                }
            }
        }
    }
}
