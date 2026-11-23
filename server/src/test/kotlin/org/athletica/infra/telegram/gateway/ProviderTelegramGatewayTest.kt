package org.athletica.infra.telegram.gateway

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.context.either
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.MessagingFixture
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.domain.clientcontacts.PhoneDigits
import org.athletica.crm.domain.telegramgateway.DbTelegramGatewayIntegrations
import org.athletica.crm.domain.telegramgateway.GatewayCode
import org.athletica.crm.domain.telegramgateway.GatewayCodeResult
import org.athletica.crm.domain.telegramgateway.GatewayCodeTtl
import org.athletica.crm.employeeContext
import org.athletica.crm.storage.asInstantOrNull
import org.athletica.crm.storage.asStringOrNull
import org.athletica.infra.messaging.telegram.mockTelegramClient
import org.athletica.infra.messaging.telegram.respondJson
import org.junit.Before
import org.slf4j.LoggerFactory
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** Тесты проверки токена и отправки кода [ProviderTelegramGateway] на заглушке Gateway API. */
class ProviderTelegramGatewayTest {
    private val orgId = OrgId.new()
    private val otherOrgId = OrgId.new()
    private val now = Instant.parse("2026-11-22T10:15:30Z")
    private val phone = PhoneDigits.from("+7 900 123-45-67") ?: error("номер")
    private val code = either { GatewayCode.from("482915") }.getOrElse { error(it.message) }
    private val ttl = either { GatewayCodeTtl.from(300) }.getOrElse { error(it.message) }
    private val requests = mutableListOf<HttpRequestData>()

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            MessagingFixture.insertOrg(orgId)
            MessagingFixture.insertOrg(otherOrgId, "Other")
        }
    }

    /** Сервис поверх заглушки, отвечающей [responses] по имени метода Gateway. */
    private fun gateway(responses: (String) -> String?): ProviderTelegramGateway =
        ProviderTelegramGateway(
            TestPostgres.db,
            DbTelegramGatewayIntegrations(),
            TelegramGatewayApi(
                listOf(
                    mockTelegramClient { request ->
                        requests += request
                        val body = responses(request.url.encodedPath.substringAfterLast('/')) ?: throw IOException("down")
                        respondJson(body)
                    },
                ),
            ),
            object : Clock {
                override fun now(): Instant = now
            },
        )

    private suspend fun insertIntegration(org: OrgId, token: String, enabled: Boolean = true) {
        orThrow {
            TestPostgres.db.sql("INSERT INTO telegram_gateway_integrations (org_id, token, enabled) VALUES (:orgId, :token, :enabled)")
                .bind("orgId", org).bind("token", token).bind("enabled", enabled).execute()
        }
    }

    private suspend fun send(gateway: ProviderTelegramGateway, org: OrgId = orgId): GatewayCodeResult = orThrow { context(employeeContext(orgId = org)) { gateway.send(phone, code, ttl) } }

    private suspend fun verify(gateway: ProviderTelegramGateway, token: String): Either<AppError, String> = either { context(employeeContext(orgId = orgId)) { gateway.verifiedToken(token) } }

    private val HttpRequestData.gatewayMethod: String get() = url.encodedPath.substringAfterLast('/')

    private val HttpRequestData.text: String get() = (body as TextContent).text

    private suspend fun balance(): Pair<String?, Instant?> =
        orThrow {
            TestPostgres.db.sql("SELECT balance::text AS balance, balance_at FROM telegram_gateway_integrations WHERE org_id = :orgId")
                .bind("orgId", orgId)
                .firstOrNull { it.asStringOrNull("balance") to it.asInstantOrNull("balance_at") } ?: error("нет интеграции")
        }

    @Test
    fun `проверка токена — checkSendAbility на недопустимый номер`() =
        runTest {
            val result = verify(gateway { """{"ok":false,"error":"PHONE_NUMBER_INVALID"}""" }, "  gw-token  ")

            assertEquals("gw-token", assertIs<Either.Right<String>>(result).value)
            assertEquals("checkSendAbility", requests.single().gatewayMethod)
            assertEquals("""{"phone_number":"+0"}""", requests.single().text)
            assertEquals("Bearer gw-token", requests.single().headers[HttpHeaders.Authorization])
        }

    @Test
    fun `ACCESS_TOKEN_INVALID — TELEGRAM_GATEWAY_TOKEN_INVALID, сеть — TELEGRAM_GATEWAY_UNAVAILABLE`() =
        runTest {
            val invalid = verify(gateway { """{"ok":false,"error":"ACCESS_TOKEN_INVALID"}""" }, "bad")
            val down = verify(gateway { null }, "gw-token")

            assertEquals("TELEGRAM_GATEWAY_TOKEN_INVALID", assertIs<Either.Left<AppError>>(invalid).value.code)
            assertEquals("TELEGRAM_GATEWAY_UNAVAILABLE", assertIs<Either.Left<AppError>>(down).value.code)
        }

    @Test
    fun `нет интеграции или она выключена — NotConfigured без обращения к Telegram`() =
        runTest {
            val gateway = gateway { error("Gateway не вызывается") }

            assertEquals<GatewayCodeResult>(GatewayCodeResult.NotConfigured, send(gateway))
            insertIntegration(orgId, "gw-token", enabled = false)
            assertEquals<GatewayCodeResult>(GatewayCodeResult.NotConfigured, send(gateway))
            assertEquals(emptyList(), requests)
        }

    @Test
    fun `код отправлен с токеном своей организации, остаток и время сохранены`() =
        runTest {
            insertIntegration(orgId, "dinamo-token")
            insertIntegration(otherOrgId, "volna-token")
            val gateway =
                gateway { method ->
                    when (method) {
                        "checkSendAbility" -> """{"ok":true,"result":{"request_id":"r-1","request_cost":0.01,"remaining_balance":4.27}}"""
                        else -> """{"ok":true,"result":{"request_id":"r-1","request_cost":0}}"""
                    }
                }

            assertEquals<GatewayCodeResult>(GatewayCodeResult.Sent("r-1"), send(gateway))

            assertEquals(listOf("checkSendAbility", "sendVerificationMessage"), requests.map { it.gatewayMethod })
            assertEquals(listOf("Bearer dinamo-token"), requests.map { it.headers[HttpHeaders.Authorization] }.distinct())
            assertTrue(requests.last().text.contains(""""request_id":"r-1""""), requests.last().text)
            assertEquals("4.27" to now, balance())
        }

    @Test
    fun `отрицательная проверка — Undeliverable без отправки`() =
        runTest {
            insertIntegration(orgId, "gw-token")

            val result = send(gateway { """{"ok":false,"error":"PHONE_NUMBER_NOT_FOUND"}""" })

            assertEquals<GatewayCodeResult>(GatewayCodeResult.Undeliverable, result)
            assertEquals(listOf("checkSendAbility"), requests.map { it.gatewayMethod })
            assertEquals(null to null, balance())
        }

    @Test
    fun `ошибки Gateway — Failed с причиной`() =
        runTest {
            insertIntegration(orgId, "gw-token")

            assertEquals<GatewayCodeResult>(GatewayCodeResult.Failed("ACCESS_TOKEN_INVALID"), send(gateway { """{"ok":false,"error":"ACCESS_TOKEN_INVALID"}""" }))
            assertEquals<GatewayCodeResult>(GatewayCodeResult.Failed("UNAVAILABLE"), send(gateway { null }))
            val sendFails =
                gateway { method ->
                    if (method == "checkSendAbility") {
                        """{"ok":true,"result":{"request_id":"r-2","remaining_balance":4.26}}"""
                    } else {
                        """{"ok":false,"error":"BALANCE_NOT_ENOUGH"}"""
                    }
                }
            assertEquals<GatewayCodeResult>(GatewayCodeResult.Failed("BALANCE_NOT_ENOUGH"), send(sendFails))
            assertEquals("4.26" to now, balance())
        }

    @Test
    fun `журнал ошибки — организация, причина и 4 цифры номера, без кода и токена`() =
        runTest {
            insertIntegration(orgId, "secret-gw-token")
            val logger = LoggerFactory.getLogger(ProviderTelegramGateway::class.java) as Logger
            val logs = ListAppender<ILoggingEvent>().apply { start() }
            logger.addAppender(logs)
            try {
                send(
                    gateway { method ->
                        if (method == "checkSendAbility") """{"ok":true,"result":{"request_id":"r-3"}}""" else """{"ok":false,"error":"CODE_INVALID"}"""
                    },
                )
            } finally {
                logger.detachAppender(logs)
            }

            val message = logs.list.single().formattedMessage
            assertTrue(message.contains(orgId.toString()), message)
            assertTrue(message.contains("CODE_INVALID"), message)
            assertTrue(message.contains("*4567"), message)
            assertFalse(message.contains("79001234567"), message)
            assertFalse(message.contains("482915"), message)
            assertFalse(message.contains("secret-gw-token"), message)
        }
}
