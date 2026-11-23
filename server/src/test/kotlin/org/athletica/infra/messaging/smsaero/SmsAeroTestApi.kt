package org.athletica.infra.messaging.smsaero

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

/** [SmsAeroApi] поверх клиента на [MockEngine] с обработчиком [handler] — заглушка SMS Aero в тестах. */
fun smsAeroTestApi(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): SmsAeroApi =
    SmsAeroApi(
        HttpClient(MockEngine { request -> handler(request) }) {
            install(HttpTimeout)
            expectSuccess = false
        },
        baseUrl = "https://sms.test/v2",
    )

/** Ответ SMS Aero с JSON-телом [body] и статусом [status]. */
fun MockRequestHandleScope.respondSmsAero(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData = respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

/** Метод SMS Aero из URL запроса (`.../v2/sign/list` → `sign/list`). */
val HttpRequestData.smsAeroMethod: String get() = url.encodedPath.removePrefix("/v2/")

/** Ответ `/auth` с принятыми учётными данными. */
const val SMS_AERO_AUTH_OK = """{"success":true,"data":null,"message":null}"""

/** Ответ на запрос с неверными учётными данными. */
const val SMS_AERO_AUTH_FAIL = """{"success":false,"data":null,"message":"Your credentials are incorrect"}"""

/** Ответ `/sign/list` с одной страницей из подписи [name] со статусом [status]. */
fun signListPage(name: String, status: Int): String = """{"success":true,"data":{"0":{"id":1,"name":"$name","status":$status},"links":{"self":"/v2/sign/list?page=1"},"totalCount":"1"},"message":null}"""
