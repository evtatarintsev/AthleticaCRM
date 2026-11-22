package org.athletica.infra.messaging.smsaero

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.Base64
import kotlin.coroutines.cancellation.CancellationException

/** Результат вызова API SMS Aero. */
sealed interface SmsAeroResult<out T> {
    /** SMS Aero ответил `success: true` со значением [value]. */
    data class Ok<T>(val value: T) : SmsAeroResult<T>

    /**
     * SMS Aero ответил отказом (HTTP 4xx или `success: false`): [status] — HTTP-статус,
     * [message] — описание от SMS Aero.
     */
    data class Error(val status: Int, val message: String) : SmsAeroResult<Nothing>

    /** SMS Aero не ответил: сеть, таймаут или ошибка сервера (HTTP 5xx). */
    data object Unavailable : SmsAeroResult<Nothing>
}

/** Учётные данные аккаунта SMS Aero: [email] и [apiKey]. */
data class SmsAeroCredentials(
    val email: String,
    val apiKey: String,
) {
    /** Без ключа: учётные данные могут попасть в журнал через `toString`. */
    override fun toString(): String = "SmsAeroCredentials(email=$email)"
}

/** Подпись (имя отправителя) в аккаунте SMS Aero: [name] и [approved] — одобрена ли модерацией. */
data class SmsAeroSign(
    val name: String,
    val approved: Boolean,
)

/**
 * Клиент API SMS Aero v2 поверх [client]. Учётные данные у каждой организации свои, поэтому
 * передаются в каждый метод и уходят заголовком Basic-авторизации. В журнал пишутся метод, email и
 * ответ SMS Aero; ключ, номер и текст сообщения — нет.
 */
class SmsAeroApi(
    private val client: HttpClient,
    private val baseUrl: String = "https://gate.smsaero.ru/v2",
) {
    private val logger = LoggerFactory.getLogger(SmsAeroApi::class.java)

    /** Проверяет, что SMS Aero принимает учётные данные [credentials]. */
    suspend fun auth(credentials: SmsAeroCredentials): SmsAeroResult<Unit> =
        when (val result = call(credentials, "auth", JsonObject(emptyMap()))) {
            is SmsAeroResult.Ok -> SmsAeroResult.Ok(Unit)
            is SmsAeroResult.Error -> result
            SmsAeroResult.Unavailable -> SmsAeroResult.Unavailable
        }

    /**
     * Подпись [name] из аккаунта [credentials] с точным совпадением имени; `Ok(null)` — такой
     * подписи нет. Перебирает страницы `/sign/list`, пока подпись не найдена или страницы не кончились.
     */
    suspend fun sign(credentials: SmsAeroCredentials, name: String): SmsAeroResult<SmsAeroSign?> {
        var page = 1
        while (page <= MAX_SIGN_PAGES) {
            val data =
                when (val result = call(credentials, "sign/list", JsonObject(emptyMap()), page)) {
                    is SmsAeroResult.Ok -> result.value
                    is SmsAeroResult.Error -> return result
                    SmsAeroResult.Unavailable -> return SmsAeroResult.Unavailable
                }
            val signs = data.listItems()
            signs.firstOrNull { it.stringField("name") == name }?.let {
                return SmsAeroResult.Ok(SmsAeroSign(name, approved = it.stringField("status") == "1"))
            }
            if (signs.isEmpty() || !data.hasNextPage(signs.size)) {
                return SmsAeroResult.Ok(null)
            }
            page++
        }
        return SmsAeroResult.Ok(null)
    }

    /** Отправляет текст [text] на номер [number] от подписи [sign]; результат — идентификатор сообщения. */
    suspend fun send(
        credentials: SmsAeroCredentials,
        number: String,
        text: String,
        sign: String,
    ): SmsAeroResult<String> {
        val body =
            buildJsonObject {
                put("number", number)
                put("text", text)
                put("sign", sign)
            }
        return when (val result = call(credentials, "sms/send", body)) {
            is SmsAeroResult.Ok ->
                (result.value as? JsonObject)?.stringField("id")?.let { SmsAeroResult.Ok(it) }
                    ?: SmsAeroResult.Error(200, "Нет id сообщения в ответе")
            is SmsAeroResult.Error -> result
            SmsAeroResult.Unavailable -> SmsAeroResult.Unavailable
        }
    }

    /** Выполняет метод [method] с телом [body] (и страницей [page], если задана); результат — поле `data`. */
    private suspend fun call(
        credentials: SmsAeroCredentials,
        method: String,
        body: JsonObject,
        page: Int? = null,
    ): SmsAeroResult<JsonElement> {
        val (status, text) =
            try {
                client.post("$baseUrl/$method") {
                    header(HttpHeaders.Authorization, credentials.basicAuthHeader())
                    page?.let { parameter("page", it) }
                    setBody(TextContent(body.toString(), ContentType.Application.Json))
                    timeout {
                        requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
                        socketTimeoutMillis = REQUEST_TIMEOUT_MILLIS
                    }
                }.let { it.status.value to it.bodyAsText() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                logger.warn("SMS Aero {} [{}]: недоступен ({})", method, credentials.email, e.javaClass.simpleName)
                return SmsAeroResult.Unavailable
            }
        if (status >= 500) {
            logger.warn("SMS Aero {} [{}]: HTTP {}", method, credentials.email, status)
            return SmsAeroResult.Unavailable
        }
        return parse(status, text).also {
            if (it is SmsAeroResult.Error) {
                logger.warn("SMS Aero {} [{}]: отказ {} — {}", method, credentials.email, it.status, it.message)
            }
        }
    }

    /** Разбирает ответ `{success, data, message}` со статусом [status] и телом [text]. */
    private fun parse(status: Int, text: String): SmsAeroResult<JsonElement> {
        val envelope =
            try {
                json.parseToJsonElement(text) as? JsonObject
            } catch (e: SerializationException) {
                null
            } ?: return SmsAeroResult.Error(status, "HTTP $status")
        val success = envelope["success"]?.jsonPrimitive?.booleanOrNull ?: false
        if (status >= 400 || !success) {
            val message = envelope.stringField("message") ?: "HTTP $status"
            return SmsAeroResult.Error(status, message)
        }
        return SmsAeroResult.Ok(envelope["data"] ?: JsonNull)
    }

    private companion object {
        /** Таймаут запроса к SMS Aero. */
        const val REQUEST_TIMEOUT_MILLIS = 10_000L

        /** Подписей на странице `/sign/list`. */
        const val SIGN_PAGE_SIZE = 50

        /** Предел перебора страниц подписей на случай зацикленной пагинации. */
        const val MAX_SIGN_PAGES = 100

        /** JSON SMS Aero: лишние поля игнорируются. */
        val json = Json { ignoreUnknownKeys = true }

        /** Заголовок Basic-авторизации `email:apiKey`. */
        fun SmsAeroCredentials.basicAuthHeader(): String = "Basic " + Base64.getEncoder().encodeToString("$email:$apiKey".toByteArray())

        /** Элементы списка: массив `data` или объект с элементами под числовыми ключами (рядом с `links`). */
        fun JsonElement.listItems(): List<JsonObject> =
            when (this) {
                is JsonArray -> filterIsInstance<JsonObject>()
                is JsonObject -> filterKeys { key -> key.all { it.isDigit() } }.values.filterIsInstance<JsonObject>()
                else -> emptyList()
            }

        /** Есть ли следующая страница: ссылка `links.next`, а без ссылок — полная страница из [count] элементов. */
        fun JsonElement.hasNextPage(count: Int): Boolean {
            val links = (this as? JsonObject)?.get("links") as? JsonObject ?: return count >= SIGN_PAGE_SIZE
            return links["next"]?.let { it !is JsonNull } ?: false
        }

        /** Строковое значение поля [name] (число — тоже строкой); `null`, если поля нет. */
        fun JsonObject.stringField(name: String): String? = get(name)?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull
    }
}

/** Создаёт HTTP-клиент для API SMS Aero: ответы с ошибкой не бросают исключений, их разбирает [SmsAeroApi]. */
fun createSmsAeroHttpClient(): HttpClient =
    HttpClient(CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
        }
        expectSuccess = false
    }
