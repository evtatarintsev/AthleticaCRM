package org.athletica.mobile

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.io.IOException
import org.athletica.crm.api.appJson
import org.athletica.crm.api.schemas.ErrorResponse
import org.athletica.mobile.api.ClientApi
import org.athletica.mobile.session.SessionStore

/**
 * Обращение к фейковому серверу: метод и путь [call] (`POST client-app/connect`), query-параметры [query],
 * тело [body] и `Authorization` [auth].
 */
data class Call(
    val call: String,
    val query: Map<String, String>,
    val body: String,
    val auth: String?,
)

/**
 * Фейковое клиентское API на `MockEngine`: ответы по схемам `shared`, журнал обращений [calls],
 * выключатель сети [online].
 */
class FakeServer {
    /** Обращения в порядке поступления. */
    val calls = mutableListOf<Call>()

    /** Есть ли связь: без неё каждый запрос падает сетевой ошибкой. */
    var online = true

    private val routes = mutableMapOf<String, MockRequestHandleScope.(Call) -> HttpResponseData>()

    /** Движок для [ClientApi]. */
    val engine =
        MockEngine { request ->
            if (!online) {
                throw IOException("Нет сети")
            }
            val call =
                Call(
                    "${request.method.value} ${request.url.encodedPath.removePrefix("/api/")}",
                    request.url.parameters.names().associateWith { request.url.parameters[it].orEmpty() },
                    request.bodyText(),
                    request.headers[HttpHeaders.Authorization],
                )
            calls += call
            routes[call.call]?.invoke(this, call) ?: respond("", HttpStatusCode.NotFound)
        }

    /** Ответ на [route] (`GET client-app/profiles`) — произвольный [handler]. */
    fun on(route: String, handler: MockRequestHandleScope.(Call) -> HttpResponseData) {
        routes[route] = handler
    }

    /** Ответ на [route] — JSON значения [value] для каждого обращения. */
    inline fun <reified T> json(route: String, crossinline value: (Call) -> T) {
        on(route) { ok(value(it)) }
    }

    /** Клиент API по этому серверу с хранилищем [store]. */
    fun api(store: SessionStore): ClientApi = ClientApi(engine, "http://test/api/", store, "Athletica (Android 14)")

    /** Обращения по маршруту [route]. */
    fun callsTo(route: String): List<Call> = calls.filter { it.call == route }
}

/** HTTP 200 с JSON значения [value]. */
inline fun <reified T> MockRequestHandleScope.ok(value: T): HttpResponseData = respond(appJson.encodeToString(value), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

/** Бизнес-ошибка HTTP 400 с кодом [code] и сообщением [message]. */
fun MockRequestHandleScope.businessError(code: String, message: String): HttpResponseData = respond(appJson.encodeToString(ErrorResponse(code, message)), HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

/** Тело запроса строкой; пусто, если тела нет. */
private fun HttpRequestData.bodyText(): String =
    when (val content = body) {
        is TextContent -> content.text
        is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
        else -> ""
    }
