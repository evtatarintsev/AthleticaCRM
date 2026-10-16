package org.athletica.infra.messaging.telegram

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

/** HTTP-клиент на [MockEngine] с обработчиком [handler] — заменяет прокси в тестах Bot API. */
fun mockTelegramClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient =
    HttpClient(MockEngine { request -> handler(request) }) {
        install(HttpTimeout)
        expectSuccess = false
    }

/** [TelegramBotApi] поверх одного клиента на [MockEngine] с обработчиком [handler]. */
fun telegramTestApi(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): TelegramBotApi = TelegramBotApi(listOf(mockTelegramClient(handler)))

/** Ответ Bot API с JSON-телом [body] и статусом [status]. */
fun MockRequestHandleScope.respondJson(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData = respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

/** Метод Bot API из URL запроса (`.../bot<token>/<method>`). */
val HttpRequestData.telegramMethod: String get() = url.encodedPath.substringAfterLast('/')
