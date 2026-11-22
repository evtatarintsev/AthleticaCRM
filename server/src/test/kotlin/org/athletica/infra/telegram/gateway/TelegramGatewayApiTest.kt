package org.athletica.infra.telegram.gateway

import arrow.core.getOrElse
import arrow.core.raise.context.either
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.athletica.crm.domain.clientcontacts.PhoneDigits
import org.athletica.crm.domain.telegramgateway.GatewayCode
import org.athletica.crm.domain.telegramgateway.GatewayCodeTtl
import org.athletica.infra.messaging.telegram.mockTelegramClient
import org.athletica.infra.messaging.telegram.respondJson
import java.net.ConnectException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

/** Тесты клиента Telegram Gateway API [TelegramGatewayApi]: заголовок, параметры и разбор ответов. */
class TelegramGatewayApiTest {
    private val phone = PhoneDigits.from("8 (900) 123-45-67") ?: error("номер")
    private val code = either { GatewayCode.from("482915") }.getOrElse { error(it.message) }
    private val ttl = either { GatewayCodeTtl.from(300) }.getOrElse { error(it.message) }

    private val HttpRequestData.json: JsonObject get() = Json.parseToJsonElement((body as TextContent).text).jsonObject

    @Test
    fun `checkSendAbility — Bearer, номер в E164 и разбор request_id с остатком`() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val api =
                TelegramGatewayApi(
                    listOf(
                        mockTelegramClient { request ->
                            requests += request
                            respondJson("""{"ok":true,"result":{"request_id":"r-1","phone_number":"79001234567","request_cost":0.01,"remaining_balance":4.27}}""")
                        },
                    ),
                )

            val result = api.checkSendAbility("gw-token", phone)

            assertEquals(GatewayResult.Ok("r-1", Money(427, Currency.USD)), result)
            val request = requests.single()
            assertEquals("https://gatewayapi.telegram.org/checkSendAbility", request.url.toString())
            assertEquals("Bearer gw-token", request.headers[HttpHeaders.Authorization])
            assertEquals("""{"phone_number":"+79001234567"}""", request.json.toString())
        }

    @Test
    fun `sendVerificationMessage — request_id, code и ttl, ответ без остатка`() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val api =
                TelegramGatewayApi(
                    listOf(
                        mockTelegramClient { request ->
                            requests += request
                            respondJson("""{"ok":true,"result":{"request_id":"r-1","phone_number":"79001234567","request_cost":0}}""")
                        },
                    ),
                )

            val result = api.sendVerificationMessage("gw-token", phone, "r-1", code, ttl)

            assertEquals(GatewayResult.Ok("r-1", null), result)
            assertEquals("Bearer gw-token", requests.single().headers[HttpHeaders.Authorization])
            assertEquals(
                """{"phone_number":"+79001234567","request_id":"r-1","code":"482915","ttl":300}""",
                requests.single().json.toString(),
            )
        }

    @Test
    fun `ok=false — строка ошибки Gateway`() =
        runTest {
            val api = TelegramGatewayApi(listOf(mockTelegramClient { respondJson("""{"ok":false,"error":"ACCESS_TOKEN_INVALID"}""", HttpStatusCode.Unauthorized) }))

            assertEquals(GatewayResult.Error("ACCESS_TOKEN_INVALID"), api.checkSendAbility("bad", phone))
        }

    @Test
    fun `не-JSON ответ — Unavailable, а не ошибка Gateway`() =
        runTest {
            val api = TelegramGatewayApi(listOf(mockTelegramClient { respondJson("<html>", HttpStatusCode.BadGateway) }))

            assertEquals(GatewayResult.Unavailable, api.checkSendAbility("t", phone))
        }

    @Test
    fun `ошибка соединения — следующий клиент, таймаут — без повтора`() =
        runBlocking {
            var reached = 0
            val healthy =
                mockTelegramClient {
                    reached++
                    respondJson("""{"ok":true,"result":{"request_id":"r"}}""")
                }
            val refused = TelegramGatewayApi(listOf(mockTelegramClient { throw ConnectException("refused") }, healthy))
            val slow =
                TelegramGatewayApi(
                    listOf(
                        mockTelegramClient {
                            delay(5_000)
                            respondJson("""{"ok":true,"result":{"request_id":"r"}}""")
                        },
                        healthy,
                    ),
                    requestTimeout = 100.milliseconds,
                )

            assertEquals(GatewayResult.Ok("r", null), refused.checkSendAbility("t", phone))
            assertEquals(GatewayResult.Unavailable, slow.checkSendAbility("t", phone))
            assertEquals(1, reached)
        }

    @Test
    fun `нет ответа за таймаут — Unavailable`() =
        runBlocking {
            val api =
                TelegramGatewayApi(
                    listOf(
                        mockTelegramClient {
                            delay(5_000)
                            respondJson("""{"ok":true,"result":{"request_id":"r"}}""")
                        },
                    ),
                    requestTimeout = 100.milliseconds,
                )

            assertEquals(GatewayResult.Unavailable, api.checkSendAbility("t", phone))
        }
}
