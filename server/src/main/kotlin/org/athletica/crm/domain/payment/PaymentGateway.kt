package org.athletica.crm.domain.payment

import arrow.core.raise.context.Raise
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.money.Money

/** Порт для интеграции с платёжными шлюзами. Реализации — в `org.athletica.infra.*`. */
interface PaymentGateway {
    /**
     * Создаёт платёж на стороне шлюза и возвращает ссылку на форму оплаты.
     * Сбой шлюза (сеть, отказ API, некорректный ответ) поднимает как [PaymentGatewayError].
     */
    context(raise: Raise<PaymentGatewayError>)
    suspend fun createPayment(request: PaymentCreateRequest): PaymentCreateResult
}

/**
 * Запрос на создание платежа.
 * [idempotencyKey] — UUID v7, генерируется на нашей стороне; обеспечивает идемпотентность повторных вызовов.
 * [orgId] — организация, инициировавшая платёж.
 */
data class PaymentCreateRequest(
    val idempotencyKey: String,
    val amount: Money,
    val description: String,
    val orgId: OrgId,
)

/**
 * Результат создания платежа на стороне шлюза.
 * [externalPaymentId] сохраняется в `payment_transactions.external_payment_id`.
 * [confirmationUrl] — URL формы оплаты для редиректа пользователя.
 */
data class PaymentCreateResult(
    val externalPaymentId: String,
    val confirmationUrl: String,
)

/**
 * Сбой платёжного шлюза. Это не [org.athletica.crm.core.errors.DomainError]:
 * маршрут ответит HTTP 500, а [message] попадёт только в лог.
 */
data class PaymentGatewayError(
    override val message: String,
) : AppError {
    override val code = "PAYMENT_GATEWAY_ERROR"
}
