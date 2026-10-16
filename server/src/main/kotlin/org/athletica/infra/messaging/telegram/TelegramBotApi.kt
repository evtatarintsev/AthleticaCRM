package org.athletica.infra.messaging.telegram

import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.Credentials
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.Authenticator
import java.net.PasswordAuthentication
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

/** Результат вызова Bot API. */
sealed interface TelegramResult<out T> {
    /** Bot API ответил `ok = true` со значением [value]. */
    data class Ok<T>(val value: T) : TelegramResult<T>

    /**
     * Bot API ответил ошибкой (`ok = false` или не-JSON ответ): [status] — `error_code` или HTTP-статус,
     * [description] — описание от Bot API. Это ответ, а не сбой прокси.
     */
    data class Error(val status: Int, val description: String) : TelegramResult<Nothing>

    /** Запрос не удалось выполнить ни через один прокси (сеть, таймаут соединения). */
    data object Unavailable : TelegramResult<Nothing>
}

/**
 * Клиент Telegram Bot API. Держит по HTTP-клиенту на каждый прокси ([clients]) и шлёт запрос через
 * последний удачный; при сетевой ошибке или таймауте повторяет через следующие по кругу.
 * Ответ с ошибкой от Bot API перебор не запускает. Токен входит в URL, поэтому в логи пишется
 * только имя метода.
 */
class TelegramBotApi(
    private val clients: List<HttpClient>,
    private val baseUrl: String = "https://api.telegram.org",
) {
    private val logger = LoggerFactory.getLogger(TelegramBotApi::class.java)
    private val current = AtomicInteger(0)

    init {
        require(clients.isNotEmpty()) { "Нужен хотя бы один HTTP-клиент Bot API" }
    }

    /** Сведения о боте с токеном [token]; проверяет, что токен действителен. */
    suspend fun getMe(token: String): TelegramResult<TgUser> = call(token, "getMe", JsonObject(emptyMap()), TgUser.serializer())

    /**
     * Обновления бота начиная с [offset]: long-poll до [timeoutSeconds] секунд, не больше [limit] штук,
     * только новые сообщения.
     */
    suspend fun getUpdates(
        token: String,
        offset: Long,
        timeoutSeconds: Int,
        limit: Int,
    ): TelegramResult<List<TgUpdate>> =
        call(
            token,
            "getUpdates",
            buildJsonObject {
                put("offset", offset)
                put("timeout", timeoutSeconds)
                put("limit", limit)
                putJsonArray("allowed_updates") { add("message") }
            },
            ListSerializer(TgUpdate.serializer()),
            requestTimeoutMillis = (timeoutSeconds + 10) * 1000L,
        )

    /** Отправляет текст [text] в чат [chatId], при необходимости с клавиатурой [replyMarkup]. */
    suspend fun sendMessage(
        token: String,
        chatId: String,
        text: String,
        replyMarkup: TgReplyMarkup? = null,
    ): TelegramResult<TgMessage> =
        call(
            token,
            "sendMessage",
            buildJsonObject {
                put("chat_id", chatId)
                put("text", text)
                replyMarkup?.let { put("reply_markup", it.toJson()) }
            },
            TgMessage.serializer(),
        )

    /** Снимает вебхук бота, если он был задан: иначе `getUpdates` отвечает 409. Накопленные обновления сохраняются. */
    suspend fun deleteWebhook(token: String): TelegramResult<Boolean> = call(token, "deleteWebhook", buildJsonObject { put("drop_pending_updates", false) }, Boolean.serializer())

    /** Выполняет метод [method] с телом [body], перебирая клиенты при сетевых ошибках. */
    private suspend fun <T> call(
        token: String,
        method: String,
        body: JsonObject,
        serializer: KSerializer<T>,
        requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
    ): TelegramResult<T> {
        val start = current.get()
        clients.indices.forEach { attempt ->
            val index = (start + attempt) % clients.size
            val text =
                try {
                    clients[index].post("$baseUrl/bot$token/$method") {
                        setBody(TextContent(body.toString(), ContentType.Application.Json))
                        timeout {
                            this.requestTimeoutMillis = requestTimeoutMillis
                            socketTimeoutMillis = requestTimeoutMillis
                        }
                    }.let { it.status.value to it.bodyAsText() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    logger.warn("Telegram {}: клиент #{} недоступен ({})", method, index, e.javaClass.simpleName)
                    null
                }
            if (text != null) {
                current.set(index)
                return parse(text.first, text.second, serializer)
            }
        }
        logger.warn("Telegram {}: ни один прокси не ответил", method)
        return TelegramResult.Unavailable
    }

    /** Разбирает ответ Bot API со статусом [status] и телом [text]. */
    private fun <T> parse(status: Int, text: String, serializer: KSerializer<T>): TelegramResult<T> {
        val envelope =
            try {
                json.parseToJsonElement(text).jsonObject
            } catch (e: SerializationException) {
                return TelegramResult.Error(status, "HTTP $status")
            } catch (e: IllegalArgumentException) {
                return TelegramResult.Error(status, "HTTP $status")
            }
        val ok = envelope["ok"]?.jsonPrimitive?.booleanOrNull ?: false
        val result = envelope["result"]
        if (!ok || result == null) {
            val code = envelope["error_code"]?.jsonPrimitive?.intOrNull ?: status
            val description = envelope["description"]?.jsonPrimitive?.contentOrNull ?: "HTTP $status"
            return TelegramResult.Error(code, description)
        }
        return TelegramResult.Ok(json.decodeFromJsonElement(serializer, result))
    }

    /** JSON-представление клавиатуры. */
    private fun TgReplyMarkup.toJson(): JsonElement =
        when (this) {
            is TgReplyMarkup.RequestContact ->
                buildJsonObject {
                    put(
                        "keyboard",
                        buildJsonArray {
                            add(
                                buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put("text", buttonText)
                                            put("request_contact", true)
                                        },
                                    )
                                },
                            )
                        },
                    )
                    put("resize_keyboard", true)
                    put("one_time_keyboard", true)
                }

            TgReplyMarkup.Remove -> buildJsonObject { put("remove_keyboard", true) }
        }

    private companion object {
        /** Таймаут обычного (не long-poll) запроса. */
        const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 15_000L

        /** JSON Bot API: лишние поля игнорируются. */
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** Таймаут соединения с прокси или Bot API: отказавший прокси не должен надолго задерживать запрос. */
private const val CONNECT_TIMEOUT_MILLIS = 5_000L

/**
 * Создаёт HTTP-клиенты Bot API: по одному на каждый прокси из [proxies] либо один прямой, если список пуст.
 * Логин HTTP-прокси передаётся через `Proxy-Authorization`, SOCKS5 — через [Authenticator] JVM.
 */
fun createTelegramHttpClients(proxies: List<TelegramProxy>): List<HttpClient> {
    installSocksAuthenticator(proxies)
    if (proxies.isEmpty()) {
        return listOf(telegramHttpClient(null))
    }
    return proxies.map { telegramHttpClient(it) }
}

/** HTTP-клиент на движке OkHttp через прокси [proxy] (`null` — напрямую). */
private fun telegramHttpClient(proxy: TelegramProxy?): HttpClient =
    HttpClient(OkHttp) {
        engine {
            this.proxy =
                when (proxy?.type) {
                    null -> null
                    TelegramProxyType.HTTP -> ProxyBuilder.http(Url("http://${proxy.host}:${proxy.port}"))
                    TelegramProxyType.SOCKS5 -> ProxyBuilder.socks(proxy.host, proxy.port)
                }
            config {
                connectTimeout(CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                val credentials = proxy?.credentials
                if (proxy?.type == TelegramProxyType.HTTP && credentials != null) {
                    proxyAuthenticator { _, response ->
                        response.request
                            .newBuilder()
                            .header("Proxy-Authorization", Credentials.basic(credentials.username, credentials.password))
                            .build()
                    }
                }
            }
        }
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
        }
        expectSuccess = false
    }

/**
 * Устанавливает [Authenticator] JVM, отвечающий логином SOCKS5-прокси из [proxies] с аутентификацией.
 * Для остальных запросов аутентификации ничего не возвращает.
 */
private fun installSocksAuthenticator(proxies: List<TelegramProxy>) {
    val socks = proxies.filter { it.type == TelegramProxyType.SOCKS5 && it.credentials != null }
    if (socks.isEmpty()) {
        return
    }
    Authenticator.setDefault(
        object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication? {
                val match = socks.firstOrNull { it.host.equals(requestingHost, ignoreCase = true) && it.port == requestingPort }
                val credentials = match?.credentials ?: return null
                return PasswordAuthentication(credentials.username, credentials.password.toCharArray())
            }
        },
    )
}
