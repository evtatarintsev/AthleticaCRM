package org.athletica.crm.routes

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.api.schemas.payment.InitiatePaymentRequest
import org.athletica.crm.api.schemas.payment.PaymentUrlResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Money
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logCreate
import org.athletica.crm.domain.payment.PaymentCreateRequest
import org.athletica.crm.domain.payment.PaymentGateway
import org.athletica.crm.domain.payment.Payments
import org.athletica.crm.storage.Database
import kotlin.uuid.Uuid

/**
 * Маршруты управления платежами (требуют JWT-авторизации).
 * POST /payments/initiate — создание платежа и получение URL формы оплаты ЮKassa.
 */
context(db: Database, audit: AuditLog)
fun RouteWithContext.paymentRoutes(
    payments: Payments,
    paymentGateway: PaymentGateway,
) {
    post<InitiatePaymentRequest, PaymentUrlResponse>("/payments/initiate") { request ->
        initiatePayment(db, audit, payments, paymentGateway, request)
    }
}

/**
 * Инициирует платёж: вызывает ЮKassa, сохраняет транзакцию и возвращает URL формы оплаты.
 * HTTP-вызов к шлюзу выполняется ВНЕ БД-транзакции, чтобы не держать соединение открытым
 * во время сетевого запроса.
 */
context(ctx: EmployeeRequestContext, raise: Raise<DomainError>)
private suspend fun initiatePayment(
    db: Database,
    audit: AuditLog,
    payments: Payments,
    paymentGateway: PaymentGateway,
    request: InitiatePaymentRequest,
): PaymentUrlResponse {
    val amount = Money(request.amount, ctx.currency)
    val idempotencyKey = Uuid.generateV7().toString()

    val gatewayResult =
        try {
            paymentGateway.createPayment(
                PaymentCreateRequest(
                    idempotencyKey = idempotencyKey,
                    amount = amount,
                    description = request.description,
                    orgId = ctx.orgId,
                ),
            )
        } catch (e: Exception) {
            raise(CommonDomainError("PAYMENT_GATEWAY_ERROR", "Ошибка платёжного шлюза: ${e.message}"))
        }

    return db.transaction {
        val payment =
            payments.create(
                gatewayName = "yookassa",
                externalPaymentId = gatewayResult.externalPaymentId,
                amount = amount,
                description = request.description,
                confirmationUrl = gatewayResult.confirmationUrl,
            )

        audit.logCreate(
            entityType = "payment_transaction",
            entityId = payment.id,
            data = """{"amount":${request.amount},"currency":"${ctx.currency.name}","externalId":"${gatewayResult.externalPaymentId}"}""",
        )

        PaymentUrlResponse(paymentId = payment.id, confirmationUrl = gatewayResult.confirmationUrl)
    }
}
