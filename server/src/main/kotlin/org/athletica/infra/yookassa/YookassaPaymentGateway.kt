package org.athletica.infra.yookassa

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.ktor.serialization.ContentConvertException
import org.athletica.crm.domain.payment.PaymentCreateRequest
import org.athletica.crm.domain.payment.PaymentCreateResult
import org.athletica.crm.domain.payment.PaymentGateway
import org.athletica.crm.domain.payment.PaymentGatewayError
import org.athletica.crm.storage.toDbDecimal
import java.io.IOException

/**
 * Адаптер [PaymentGateway] для ЮKassa.
 * Преобразует доменный [PaymentCreateRequest] в HTTP-вызов к API ЮKassa.
 * Исключения HTTP-клиента — стык с инфраструктурой: здесь они превращаются в [PaymentGatewayError].
 */
class YookassaPaymentGateway(
    private val api: YookassaApi,
) : PaymentGateway {
    context(raise: Raise<PaymentGatewayError>)
    override suspend fun createPayment(request: PaymentCreateRequest): PaymentCreateResult {
        val response =
            try {
                api.createPayment(
                    idempotencyKey = request.idempotencyKey,
                    // toDbDecimal() → BigDecimal с нужным масштабом (копейки → рубли.копейки),
                    // toPlainString() гарантирует формат без экспоненты ("1200.50", а не "1.2E+3")
                    amountValue = request.amount.toDbDecimal().toPlainString(),
                    currency = request.amount.currency.name,
                    description = request.description,
                )
            } catch (e: YookassaApiException) {
                raise(PaymentGatewayError("ЮKassa ответила ошибкой HTTP ${e.httpStatus}: ${e.message}"))
            } catch (e: IOException) {
                raise(PaymentGatewayError("ЮKassa недоступна: ${e.message}"))
            } catch (e: ContentConvertException) {
                raise(PaymentGatewayError("Некорректный ответ ЮKassa: ${e.message}"))
            }

        return PaymentCreateResult(
            externalPaymentId = response.id,
            confirmationUrl =
                response.confirmation?.confirmationUrl
                    ?: raise(PaymentGatewayError("ЮKassa не вернула confirmation_url для платежа ${response.id}")),
        )
    }
}
