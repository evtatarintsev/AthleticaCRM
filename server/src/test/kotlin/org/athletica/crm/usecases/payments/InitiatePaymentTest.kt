package org.athletica.crm.usecases.payments

import arrow.core.Either
import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import arrow.core.raise.context.raise
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.payment.PaymentUrlResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.athletica.crm.domain.audit.PostgresAuditLog
import org.athletica.crm.domain.payment.DbPayments
import org.athletica.crm.domain.payment.PaymentCreateRequest
import org.athletica.crm.domain.payment.PaymentCreateResult
import org.athletica.crm.domain.payment.PaymentGateway
import org.athletica.crm.domain.payment.PaymentGatewayError
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Тесты сценария [initiatePayment]: проверка суммы до обращения к шлюзу и сбой шлюза. */
class InitiatePaymentTest {
    private val payments = DbPayments()
    private val audit = PostgresAuditLog()

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    @Test
    fun `нулевая сумма отклоняется без вызова шлюза`() = assertRejectedWithoutGateway(Money(0, Currency.RUB))

    @Test
    fun `отрицательная сумма отклоняется без вызова шлюза`() = assertRejectedWithoutGateway(Money(-100_00, Currency.RUB))

    @Test
    fun `положительная сумма создаёт ожидающий платёж`() =
        runTest {
            val ctx = paymentTestContext()
            val gateway = RecordingGateway()

            val result = either { initiate(ctx, gateway, Money(1_000_00, Currency.RUB)) }

            val response = assertIs<Either.Right<PaymentUrlResponse>>(result).value
            assertEquals("https://yookassa.ru/pay/ext_1", response.confirmationUrl)
            assertEquals(1, gateway.calls)
            assertEquals("pending", paymentStatus("ext_1"))
        }

    @Test
    fun `сбой шлюза поднимается как PaymentGatewayError без записи платежа`() =
        runTest {
            val ctx = paymentTestContext()
            val gateway = RecordingGateway(failure = PaymentGatewayError("шлюз недоступен"))

            val result = either { initiate(ctx, gateway, Money(1_000_00, Currency.RUB)) }

            assertIs<Either.Left<PaymentGatewayError>>(result)
            assertEquals(0, paymentCount(ctx.orgId))
        }

    private fun assertRejectedWithoutGateway(amount: Money) =
        runTest {
            val ctx = paymentTestContext()
            val gateway = RecordingGateway()

            val result = either { initiate(ctx, gateway, amount) }

            val error = assertIs<Either.Left<AppError>>(result).value
            assertEquals("PAYMENT_AMOUNT_NOT_POSITIVE", error.code)
            assertEquals(0, gateway.calls)
            assertEquals(0, paymentCount(ctx.orgId))
        }

    context(raise: Raise<AppError>)
    private suspend fun initiate(
        ctx: EmployeeRequestContext,
        gateway: PaymentGateway,
        amount: Money,
    ) = context(ctx) { initiatePayment(TestPostgres.db, audit, payments, gateway, amount, "Пополнение") }

    /** Шлюз-заглушка: считает вызовы и либо возвращает платёж `ext_N`, либо поднимает [failure]. */
    private class RecordingGateway(
        private val failure: PaymentGatewayError? = null,
    ) : PaymentGateway {
        /** Число обращений к шлюзу. */
        var calls = 0
            private set

        context(raise: Raise<PaymentGatewayError>)
        override suspend fun createPayment(request: PaymentCreateRequest): PaymentCreateResult {
            calls++
            if (failure != null) {
                raise(failure)
            }
            return PaymentCreateResult("ext_$calls", "https://yookassa.ru/pay/ext_$calls")
        }
    }
}
