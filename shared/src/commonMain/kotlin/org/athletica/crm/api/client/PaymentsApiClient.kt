package org.athletica.crm.api.client

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.athletica.crm.api.schemas.payment.InitiatePaymentRequest
import org.athletica.crm.api.schemas.payment.PaymentUrlResponse
import org.athletica.crm.core.money.Money

/** Клиент API для инициирования платежей. */
class PaymentsApiClient(private val http: HttpClient) {
    /**
     * Инициирует пополнение баланса на сумму [amount].
     * Возвращает [PaymentUrlResponse] с URL формы оплаты ЮKassa.
     */
    suspend fun initiate(amount: Money, description: String): Either<ApiClientError, PaymentUrlResponse> =
        requestCatching {
            http.post("/api/payments/initiate") {
                contentType(ContentType.Application.Json)
                setBody(InitiatePaymentRequest(amount = amount.minorUnits, description = description))
            }
        }
}
