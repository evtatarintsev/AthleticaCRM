package org.athletica.crm.routes

import arrow.core.Either
import arrow.core.raise.context.either
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.athletica.crm.Di
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.api.schemas.telegramgateway.TelegramGatewayCodeOutcome
import org.athletica.crm.api.schemas.telegramgateway.TelegramGatewayIntegrationSchema
import org.athletica.crm.api.schemas.telegramgateway.TelegramGatewaySettingsResponse
import org.athletica.crm.api.schemas.telegramgateway.TelegramGatewayTestCodeResponse
import org.athletica.crm.configureServer
import org.athletica.crm.core.Lang
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.athletica.crm.sessionAccessToken
import org.athletica.crm.testDi
import org.athletica.crm.testJwtConfig
import org.athletica.crm.testName
import org.athletica.infra.messaging.telegram.mockTelegramClient
import org.athletica.infra.messaging.telegram.respondJson
import org.athletica.infra.telegram.gateway.TelegramGatewayApi
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Тесты маршрутов настроек Telegram Gateway: маска токена, проверка токена и тестовый код. */
class TelegramGatewayRoutesTest {
    private lateinit var di: Di
    private lateinit var token: String
    private val json = Json { ignoreUnknownKeys = true }
    private val requests = mutableListOf<HttpRequestData>()

    @Before
    fun setUp() {
        TestPostgres.truncate()
        val api =
            TelegramGatewayApi(
                listOf(
                    mockTelegramClient { request ->
                        requests += request
                        val body = (request.body as TextContent).text
                        when {
                            request.headers[HttpHeaders.Authorization] == "Bearer revoked-token" -> respondJson("""{"ok":false,"error":"ACCESS_TOKEN_INVALID"}""")
                            body.contains("\"+0\"") -> respondJson("""{"ok":false,"error":"PHONE_NUMBER_INVALID"}""")
                            else -> respondJson("""{"ok":true,"result":{"request_id":"r-1","remaining_balance":4.27}}""")
                        }
                    },
                ),
            )
        di = testDi().copy(telegramGatewayApi = api)
    }

    /** Регистрирует организацию и запоминает токен её владельца. */
    private suspend fun signUp() {
        val result =
            either {
                di.database.transaction {
                    di.auth.signup(
                        SignUpRequest(
                            companyName = testName("Acme"),
                            userName = testName("John"),
                            login = "owner@example.com",
                            password = "password123",
                            timezone = "UTC",
                            currency = Currency.RUB,
                        ),
                        lang = Lang.RU,
                        clientIp = null,
                    )
                }
            }
        token = testJwtConfig.sessionAccessToken(assertIs<Either.Right<AuthenticatedUser>>(result).value)
    }

    private suspend fun ApplicationTestBuilder.settings(): TelegramGatewaySettingsResponse {
        val response = client.get("/api/telegram-gateway/settings") { header(HttpHeaders.Authorization, "Bearer $token") }
        assertEquals(HttpStatusCode.OK, response.status)
        return json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.post(path: String, body: String): HttpResponse =
        client.post("/api/telegram-gateway/$path") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.testCode(): TelegramGatewayTestCodeResponse {
        val response = post("test-code", """{"phone":"+7 900 123-45-67"}""")
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        val sentCode = requests.lastOrNull { (it.body as TextContent).text.contains("\"code\"") }?.let { json.parseToJsonElement((it.body as TextContent).text).jsonObject["code"]?.jsonPrimitive?.content }
        sentCode?.let { assertFalse(response.bodyAsText().contains(it), response.bodyAsText()) }
        return json.decodeFromString(response.bodyAsText())
    }

    @Test
    fun `подключение, маска вместо токена, отказ недействительному и тестовый код`() =
        runTest {
            signUp()
            testApplication {
                application { context(di) { configureServer() } }

                assertNull(settings().integration)

                val revoked = post("save-token", """{"token":"revoked-token"}""")
                assertTrue(revoked.bodyAsText().contains("TELEGRAM_GATEWAY_TOKEN_INVALID"), revoked.bodyAsText())
                assertNull(settings().integration)

                val saved = post("save-token", """{"token":" gw-token-abcd1234 "}""")
                assertFalse(saved.bodyAsText().contains("gw-token"), saved.bodyAsText())
                assertEquals(TelegramGatewayIntegrationSchema("••••1234", enabled = true, balance = null), settings().integration)

                val replaced = post("save-token", """{"token":"revoked-token"}""")
                assertTrue(replaced.bodyAsText().contains("TELEGRAM_GATEWAY_TOKEN_INVALID"), replaced.bodyAsText())

                assertEquals(HttpStatusCode.OK, post("set-enabled", """{"enabled":false}""").status)
                assertEquals(TelegramGatewayCodeOutcome.NOT_CONFIGURED, testCode().outcome)

                assertEquals(HttpStatusCode.OK, post("set-enabled", """{"enabled":true}""").status)
                requests.clear()
                assertEquals(TelegramGatewayTestCodeResponse(TelegramGatewayCodeOutcome.SENT, null), testCode())
                assertEquals(listOf("Bearer gw-token-abcd1234"), requests.map { it.headers[HttpHeaders.Authorization] }.distinct())
                assertEquals(Money(427, Currency.USD), settings().integration?.balance?.amount)

                post("save-token", """{"token":"short"}""")
                val afterShort = client.get("/api/telegram-gateway/settings") { header(HttpHeaders.Authorization, "Bearer $token") }.bodyAsText()
                assertFalse(afterShort.contains("short"), afterShort)
                assertEquals("••••", settings().integration?.tokenMask)
                assertEquals(true, settings().integration?.enabled)
            }
        }
}
