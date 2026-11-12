package org.athletica.crm.usecases.payments

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.payment.PaymentUrlResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.money.Money
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logCreate
import org.athletica.crm.domain.payment.PaymentCreateRequest
import org.athletica.crm.domain.payment.PaymentGateway
import org.athletica.crm.domain.payment.Payments
import org.athletica.crm.domain.payment.ensurePaymentAmountPositive
import org.athletica.crm.storage.Database
import kotlin.uuid.Uuid

/**
 * Инициирует платёж на [amount] с назначением [description]: создаёт платёж у шлюза,
 * сохраняет ожидающую транзакцию и возвращает URL формы оплаты.
 *
 * Сумма проверяется до обращения к шлюзу. Сетевой вызов выполняется вне БД-транзакции,
 * чтобы не держать соединение во время запроса; сбой шлюза поднимается как
 * [org.athletica.crm.domain.payment.PaymentGatewayError].
 */
context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
suspend fun initiatePayment(
    db: Database,
    audit: AuditLog,
    payments: Payments,
    paymentGateway: PaymentGateway,
    amount: Money,
    description: String,
): PaymentUrlResponse {
    ensurePaymentAmountPositive(amount)

    val gatewayResult =
        paymentGateway.createPayment(
            PaymentCreateRequest(
                idempotencyKey = Uuid.generateV7().toString(),
                amount = amount,
                description = description,
                orgId = ctx.orgId,
            ),
        )

    return db.transaction {
        val payment =
            payments.create(
                gatewayName = "yookassa",
                externalPaymentId = gatewayResult.externalPaymentId,
                amount = amount,
                description = description,
                confirmationUrl = gatewayResult.confirmationUrl,
            )

        audit.logCreate(
            entityType = "payment_transaction",
            entityId = payment.id,
            data = """{"amount":${amount.minorUnits},"currency":"${amount.currency.name}","externalId":"${gatewayResult.externalPaymentId}"}""",
        )

        PaymentUrlResponse(paymentId = payment.id, confirmationUrl = gatewayResult.confirmationUrl)
    }
}
