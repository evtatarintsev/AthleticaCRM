package org.athletica.infra.telegram.gateway

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.athletica.crm.domain.clientcontacts.PhoneDigits
import org.athletica.crm.domain.telegramgateway.GatewayCode
import org.athletica.crm.domain.telegramgateway.GatewayCodeTtl
import org.slf4j.LoggerFactory
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Ответ Telegram Gateway API. */
sealed interface GatewayResult {
    /** `ok = true`: [requestId] — идентификатор запроса Gateway, [remainingBalance] — остаток, если запрос был платным. */
    data class Ok(val requestId: String, val remainingBalance: Money?) : GatewayResult

    /** `ok = false` со строкой ошибки [error] от Gateway; не-JSON ответ — `HTTP <статус>`. */
    data class Error(val error: String) : GatewayResult

    /** Ни один HTTP-клиент не получил ответа: сеть или таймаут. */
    data object Unavailable : GatewayResult
}

/**
 * Клиент Telegram Gateway API. Токен аккаунта передаётся в каждый вызов заголовком
 * `Authorization: Bearer`, поэтому один клиент обслуживает все организации. Перебирает
 * [clients] (по одному на прокси `telegram.proxies`) при сетевой ошибке или таймауте, как
 * `TelegramBotApi`. Запросы не логируются: в них токен и код, в журнал попадает только метод.
 * [requestTimeout] — предел ожидания ответа одного клиента.
 */
class TelegramGatewayApi(
    private val clients: List<HttpClient>,
    private val baseUrl: String = "https://gatewayapi.telegram.org",
    private val requestTimeout: Duration = 5.seconds,
) {
    private val logger = LoggerFactory.getLogger(TelegramGatewayApi::class.java)
    private val current = AtomicInteger(0)

    init {
        require(clients.isNotEmpty()) { "Нужен хотя бы один HTTP-клиент Gateway API" }
    }

    /** Можно ли отправить код на [phone]; платный, только если отправить можно. */
    suspend fun checkSendAbility(token: String, phone: PhoneDigits): GatewayResult = call(token, "checkSendAbility", buildJsonObject { put("phone_number", phone.e164) })

    /**
     * Отправляет код [code] на [phone] со сроком [ttl]. [requestId] из [checkSendAbility] делает
     * отправку бесплатной: оплата уже прошла при проверке.
     */
    suspend fun sendVerificationMessage(
        token: String,
        phone: PhoneDigits,
        requestId: String,
        code: GatewayCode,
        ttl: GatewayCodeTtl,
    ): GatewayResult =
        call(
            token,
            "sendVerificationMessage",
            buildJsonObject {
                put("phone_number", phone.e164)
                put("request_id", requestId)
                put("code", code.value)
                put("ttl", ttl.seconds)
            },
        )

    /** Выполняет метод [method] с телом [body], перебирая клиенты при сетевых ошибках. */
    private suspend fun call(token: String, method: String, body: JsonObject): GatewayResult {
        val start = current.get()
        clients.indices.forEach { attempt ->
            val index = (start + attempt) % clients.size
            val response =
                try {
                    clients[index].post("$baseUrl/$method") {
                        bearerAuth(token)
                        setBody(TextContent(body.toString(), ContentType.Application.Json))
                        timeout {
                            requestTimeoutMillis = requestTimeout.inWholeMilliseconds
                            socketTimeoutMillis = requestTimeout.inWholeMilliseconds
                        }
                    }.let { it.status.value to it.bodyAsText() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    logger.warn("Telegram Gateway {}: клиент #{} недоступен ({})", method, index, e.javaClass.simpleName)
                    null
                }
            if (response != null) {
                current.set(index)
                return parse(response.first, response.second)
            }
        }
        logger.warn("Telegram Gateway {}: ни один прокси не ответил", method)
        return GatewayResult.Unavailable
    }

    /** Разбирает ответ со статусом [status] и телом [text]. */
    private fun parse(status: Int, text: String): GatewayResult {
        val envelope =
            try {
                json.parseToJsonElement(text).jsonObject
            } catch (e: SerializationException) {
                return GatewayResult.Error("HTTP $status")
            } catch (e: IllegalArgumentException) {
                return GatewayResult.Error("HTTP $status")
            }
        val ok = envelope["ok"]?.jsonPrimitive?.booleanOrNull ?: false
        val result = envelope["result"] as? JsonObject
        val requestId = result?.get("request_id")?.jsonPrimitive?.contentOrNull
        if (!ok || requestId == null) {
            return GatewayResult.Error(envelope["error"]?.jsonPrimitive?.contentOrNull ?: "HTTP $status")
        }
        val balance = (result["remaining_balance"] as? JsonPrimitive)?.contentOrNull?.toBigDecimalOrNull()
        return GatewayResult.Ok(requestId, balance?.toUsd())
    }

    private companion object {
        /** JSON Gateway API: лишние поля игнорируются. */
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** Номер в формате E.164: `+` и цифры. */
private val PhoneDigits.e164: String get() = "+$value"

/** Остаток Gateway в долларах, округлённый до центов. */
private fun BigDecimal.toUsd(): Money = Money(setScale(Currency.USD.fractionDigits, RoundingMode.HALF_UP).movePointRight(Currency.USD.fractionDigits).longValueExact(), Currency.USD)
