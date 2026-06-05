package org.athletica.infra.yookassa

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** HTTP-клиент для API ЮKassa. */
class YookassaApi(
    private val httpClient: HttpClient,
    private val config: YookassaConfig,
) {
    /**
     * Создаёт одностадийный платёж через API ЮKassa.
     * [idempotencyKey] — UUID v7, наш внутренний ID; ЮKassa использует его для дедупликации повторных запросов.
     * [amountValue] — сумма в основных единицах с десятичной точкой, например "1200.50".
     * При HTTP-ошибке выбрасывает [YookassaApiException] с описанием из тела ответа.
     */
    suspend fun createPayment(
        idempotencyKey: String,
        amountValue: String,
        currency: String,
        description: String,
    ): YookassaPaymentResponse {
        val response =
            httpClient.post("${config.endpointUrl}/payments") {
                header("Idempotence-Key", idempotencyKey)
                header(HttpHeaders.Authorization, config.basicAuthHeader)
                contentType(ContentType.Application.Json)
                setBody(
                    YookassaCreatePaymentRequest(
                        amount = YookassaAmount(value = amountValue, currency = currency),
                        confirmation = YookassaConfirmationRequest(type = "redirect", returnUrl = config.returnUrl),
                        description = description,
                        capture = true,
                    ),
                )
            }

        if (!response.status.isSuccess()) {
            response.throwYookassaError()
        }

        return response.body()
    }
}

/** Создаёт настроенный [HttpClient] для обращений к API ЮKassa. */
fun createYookassaHttpClient(engineFactory: HttpClientEngineFactory<*>): HttpClient =
    HttpClient(engineFactory) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 5_000
        }
    }

// ── Request schemas ───────────────────────────────────────────────────────────

@Serializable
private data class YookassaCreatePaymentRequest(
    val amount: YookassaAmount,
    val confirmation: YookassaConfirmationRequest,
    val description: String,
    /** true — одностадийная оплата: деньги списываются сразу при подтверждении. */
    val capture: Boolean,
)

@Serializable
private data class YookassaConfirmationRequest(
    val type: String,
    @SerialName("return_url")
    val returnUrl: String,
)

// ── Response / Webhook schemas ────────────────────────────────────────────────

/** Сумма в API ЮKassa: строка с десятичной точкой + код валюты. */
@Serializable
data class YookassaAmount(
    val value: String,
    val currency: String,
)

/** Ответ API ЮKassa на создание платежа. */
@Serializable
data class YookassaPaymentResponse(
    val id: String,
    val status: String,
    val amount: YookassaAmount,
    val confirmation: YookassaConfirmationResponse?,
)

/** Поле `confirmation` в ответе API ЮKassa. */
@Serializable
data class YookassaConfirmationResponse(
    val type: String,
    @SerialName("confirmation_url")
    val confirmationUrl: String? = null,
)

/** Структура webhook-уведомления от ЮKassa. */
@Serializable
data class YookassaWebhook(
    val type: String,
    val event: String,
    @SerialName("object")
    val paymentObject: YookassaWebhookPayment,
)

/** Объект платежа в теле webhook. */
@Serializable
data class YookassaWebhookPayment(
    val id: String,
    val status: String,
    val amount: YookassaAmount,
)

/** Ответ API ЮKassa при ошибке (HTTP 4xx / 5xx). */
@Serializable
private data class YookassaErrorResponse(
    val type: String = "error",
    val id: String? = null,
    val code: String? = null,
    val description: String? = null,
    val parameter: String? = null,
)

/** Ошибка, возвращённая API ЮKassa. [httpStatus] — HTTP-статус ответа. */
class YookassaApiException(
    val httpStatus: Int,
    override val message: String,
) : Exception(message)

/**
 * Считывает тело ответа как [YookassaErrorResponse] и выбрасывает [YookassaApiException]
 * с человекочитаемым описанием ошибки.
 */
private suspend fun HttpResponse.throwYookassaError(): Nothing {
    val error =
        try {
            body<YookassaErrorResponse>()
        } catch (e: Exception) {
            null
        }
    val detail =
        buildString {
            if (error?.description != null) {
                append(error.description)
            }
            if (error?.parameter != null) {
                append(" (параметр: ${error.parameter})")
            }
            if (error?.code != null) {
                append(" [${error.code}]")
            }
            if (isEmpty()) {
                append("HTTP ${status.value}")
            }
        }
    throw YookassaApiException(status.value, detail)
}
