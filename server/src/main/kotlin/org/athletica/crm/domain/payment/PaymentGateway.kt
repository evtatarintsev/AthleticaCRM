package org.athletica.crm.domain.payment

import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.money.Money

/** Порт для интеграции с платёжными шлюзами. Реализации — в `org.athletica.infra.*`. */
interface PaymentGateway {
    /**
     * Создаёт платёж на стороне шлюза и возвращает ссылку на форму оплаты.
     * Бросает исключение при сетевых ошибках — вызывающий код оборачивает в try/catch.
     */
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
