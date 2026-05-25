package org.athletica.infra.yookassa

import org.athletica.crm.domain.payment.PaymentCreateRequest
import org.athletica.crm.domain.payment.PaymentCreateResult
import org.athletica.crm.domain.payment.PaymentGateway
import org.athletica.crm.storage.toDbDecimal

/**
 * Адаптер [PaymentGateway] для ЮKassa.
 * Преобразует доменный [PaymentCreateRequest] в HTTP-вызов к API ЮKassa.
 */
class YookassaPaymentGateway(
    private val api: YookassaApi,
) : PaymentGateway {
    override suspend fun createPayment(request: PaymentCreateRequest): PaymentCreateResult {
        val response =
            api.createPayment(
                idempotencyKey = request.idempotencyKey,
                // toDbDecimal() → BigDecimal с нужным масштабом (копейки → рубли.копейки),
                // toPlainString() гарантирует формат без экспоненты ("1200.50", а не "1.2E+3")
                amountValue = request.amount.toDbDecimal().toPlainString(),
                currency = request.amount.currency.name,
                description = request.description,
            )

        return PaymentCreateResult(
            externalPaymentId = response.id,
            confirmationUrl =
                response.confirmation?.confirmationUrl
                    ?: error("ЮKassa не вернула confirmation_url для платежа ${response.id}"),
        )
    }
}
