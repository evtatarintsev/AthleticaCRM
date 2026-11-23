package org.athletica.infra.messaging.smsaero

import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

/** Тесты клиента API SMS Aero [SmsAeroApi]: авторизация, параметры запросов и разбор ответов. */
class SmsAeroApiTest {
    private val credentials = SmsAeroCredentials("club@example.com", "secret-key")

    @Test
    fun `отправка — Basic-авторизация, номер, текст и подпись в теле, id из ответа`() =
        runTest {
            var auth = ""
            var body = ""
            var method = ""
            val api =
                smsAeroTestApi { request ->
                    auth = request.headers[HttpHeaders.Authorization].orEmpty()
                    body = (request.body as TextContent).text
                    method = request.smsAeroMethod
                    respondSmsAero("""{"success":true,"data":{"id":12345,"number":"79991234567","status":8},"message":null}""")
                }

            val result = api.send(credentials, "79991234567", "Привет", "Dinamo")

            assertEquals(SmsAeroResult.Ok("12345"), result)
            assertEquals("sms/send", method)
            assertEquals("Basic " + Base64.getEncoder().encodeToString("club@example.com:secret-key".toByteArray()), auth)
            assertEquals("""{"number":"79991234567","text":"Привет","sign":"Dinamo"}""", body)
        }

    @Test
    fun `success false — Error с сообщением SMS Aero`() =
        runTest {
            val api = smsAeroTestApi { respondSmsAero(SMS_AERO_AUTH_FAIL, HttpStatusCode.Unauthorized) }

            assertEquals(SmsAeroResult.Error(401, "Your credentials are incorrect"), api.auth(credentials))
        }

    @Test
    fun `success true на auth — Ok`() =
        runTest {
            val api = smsAeroTestApi { respondSmsAero(SMS_AERO_AUTH_OK) }

            assertEquals(SmsAeroResult.Ok(Unit), api.auth(credentials))
        }

    @Test
    fun `подпись на второй странице находится по точному имени`() =
        runTest {
            val pages = mutableListOf<String?>()
            val api =
                smsAeroTestApi { request ->
                    val page = request.url.parameters["page"]
                    pages += page
                    when (page) {
                        "1" ->
                            respondSmsAero(
                                """{"success":true,"data":{"0":{"id":1,"name":"dinamo","status":1},"links":{"next":"/v2/sign/list?page=2"}},"message":null}""",
                            )
                        else ->
                            respondSmsAero("""{"success":true,"data":{"0":{"id":2,"name":"Dinamo","status":2},"links":{"self":"/v2/sign/list?page=2"}},"message":null}""")
                    }
                }

            assertEquals(SmsAeroResult.Ok(SmsAeroSign("Dinamo", approved = false)), api.sign(credentials, "Dinamo"))
            assertEquals(listOf<String?>("1", "2"), pages)
        }

    @Test
    fun `подписи нет ни на одной странице — Ok null`() =
        runTest {
            val api = smsAeroTestApi { respondSmsAero(signListPage("Other", 1)) }

            assertEquals<SmsAeroResult<SmsAeroSign?>>(SmsAeroResult.Ok(null), api.sign(credentials, "Dinamo"))
        }

    @Test
    fun `HTTP 5xx — Unavailable`() =
        runTest {
            val api = smsAeroTestApi { respondSmsAero("Bad gateway", HttpStatusCode.BadGateway) }

            assertEquals(SmsAeroResult.Unavailable, api.send(credentials, "79991234567", "т", "Dinamo"))
        }

    @Test
    fun `таймаут — Unavailable`() =
        runTest {
            val api = smsAeroTestApi { request -> throw HttpRequestTimeoutException(request.url.toString(), 10_000) }

            assertEquals(SmsAeroResult.Unavailable, api.auth(credentials))
        }

    @Test
    fun `таймаут отправки — NoResponse, SMS могло уйти`() =
        runTest {
            val api = smsAeroTestApi { request -> throw HttpRequestTimeoutException(request.url.toString(), 10_000) }

            assertEquals(SmsAeroResult.NoResponse, api.send(credentials, "79991234567", "т", "Dinamo"))
        }

    @Test
    fun `хост не резолвится — Unavailable, а не исключение`() =
        runTest {
            val api = SmsAeroApi(createSmsAeroHttpClient(), baseUrl = "https://sms-aero.invalid/v2")

            assertEquals(SmsAeroResult.Unavailable, api.auth(credentials))
        }
}
