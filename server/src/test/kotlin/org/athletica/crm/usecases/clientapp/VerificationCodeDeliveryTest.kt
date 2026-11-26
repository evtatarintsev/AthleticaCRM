package org.athletica.crm.usecases.clientapp

import arrow.core.raise.context.Raise
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.clientapp.ClientAppFixture
import org.athletica.crm.clientapp.RecordingSender
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.domain.channels.ChannelTransport
import org.athletica.crm.domain.channels.ChannelTransports
import org.athletica.crm.domain.channels.SendResult
import org.athletica.crm.domain.clientapp.DbClientAppAccesses
import org.athletica.crm.domain.clientapp.auth.CodeIssue
import org.athletica.crm.domain.clientapp.auth.DbClientAppCodes
import org.athletica.crm.domain.clientapp.codes.CodeChannel
import org.athletica.crm.domain.clientapp.codes.CodeDeliveryError
import org.athletica.crm.domain.clientapp.codes.SmsVerificationCodeSender
import org.athletica.crm.domain.clientapp.codes.TelegramVerificationCodeSender
import org.athletica.crm.domain.clientapp.codes.VerificationCodeSender
import org.athletica.crm.domain.clientcontacts.PhoneDigits
import org.athletica.crm.domain.telegramgateway.GatewayCode
import org.athletica.crm.domain.telegramgateway.GatewayCodeResult
import org.athletica.crm.domain.telegramgateway.GatewayCodeTtl
import org.athletica.crm.domain.telegramgateway.TelegramGateway
import org.athletica.crm.storage.asStringOrNull
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/** Тесты политики доставки кода ([VerificationCodeDelivery]) и отправителей TELEGRAM и SMS за счёт организаций. */
class VerificationCodeDeliveryTest {
    private val phone = "+7 903 123-45-67"
    private val digits = checkNotNull(PhoneDigits.from(phone))
    private val codes = DbClientAppCodes()

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    /** Выпускает код на [digits] и доставляет его отправителями [senders]; [viaSms] — запрошена SMS. Возвращает записанный канал. */
    private suspend fun deliver(senders: List<VerificationCodeSender>, viaSms: Boolean = false): String? =
        orThrow {
            val issued = TestPostgres.db.transaction { codes.issue(digits, Clock.System.now()) } as CodeIssue.Issued
            VerificationCodeDelivery(TestPostgres.db, DbClientAppAccesses(), codes, senders).deliver(digits, issued.id, issued.code, viaSms)
            TestPostgres.db.sql("SELECT channel FROM client_app_codes WHERE id = :id").bind("id", issued.id).firstOrNull { it.asStringOrNull("channel") ?: "" }
                ?.ifEmpty { null }
        }

    @Test
    fun `посторонний номер — доставка не запускается`() =
        runTest {
            val telegram = RecordingSender(CodeChannel.TELEGRAM)
            val sms = RecordingSender(CodeChannel.SMS)

            assertNull(deliver(listOf(telegram, sms)))
            assertTrue(telegram.sent.isEmpty() && sms.sent.isEmpty())
        }

    @Test
    fun `номер без приглашения — доставка не запускается`() =
        runTest {
            orThrow { ClientAppFixture.client(ClientAppFixture.org("Динамо", gateway = true), phone, status = null) }
            val telegram = RecordingSender(CodeChannel.TELEGRAM)

            assertNull(deliver(listOf(telegram)))
            assertTrue(telegram.sent.isEmpty())
        }

    @Test
    fun `кабинет выключен — организация не платит`() =
        runTest {
            orThrow { ClientAppFixture.client(ClientAppFixture.org("Динамо", cabinet = false, gateway = true, sms = true), phone) }
            val telegram = RecordingSender(CodeChannel.TELEGRAM)
            val sms = RecordingSender(CodeChannel.SMS)

            assertNull(deliver(listOf(telegram, sms)))
            assertTrue(telegram.sent.isEmpty() && sms.sent.isEmpty())
        }

    @Test
    fun `по умолчанию код уходит в Telegram`() =
        runTest {
            orThrow { ClientAppFixture.client(ClientAppFixture.org("Динамо"), phone) }
            val sms = RecordingSender(CodeChannel.SMS)

            assertEquals("TELEGRAM", deliver(listOf(RecordingSender(CodeChannel.TELEGRAM), sms)))
            assertTrue(sms.sent.isEmpty())
        }

    @Test
    fun `Telegram не доставил — код сразу уходит по SMS`() =
        runTest {
            orThrow { ClientAppFixture.client(ClientAppFixture.org("Динамо"), phone) }
            val sms = RecordingSender(CodeChannel.SMS)

            assertEquals("SMS", deliver(listOf(RecordingSender(CodeChannel.TELEGRAM, CodeDeliveryError.Undeliverable), sms)))
            assertEquals(listOf(digits), sms.sent)
        }

    @Test
    fun `отказ провайдера Telegram не переводит код в SMS`() =
        runTest {
            orThrow { ClientAppFixture.client(ClientAppFixture.org("Динамо"), phone) }
            val sms = RecordingSender(CodeChannel.SMS)

            assertNull(deliver(listOf(RecordingSender(CodeChannel.TELEGRAM, CodeDeliveryError.Failed("X")), sms)))
            assertTrue(sms.sent.isEmpty())
        }

    @Test
    fun `запрос SMS обходит Telegram`() =
        runTest {
            orThrow { ClientAppFixture.client(ClientAppFixture.org("Динамо"), phone) }
            val telegram = RecordingSender(CodeChannel.TELEGRAM)

            assertEquals("SMS", deliver(listOf(telegram, RecordingSender(CodeChannel.SMS)), viaSms = true))
            assertTrue(telegram.sent.isEmpty())
        }

    @Test
    fun `Telegram — за счёт организации с самым свежим приглашением и включённым Gateway`() =
        runTest {
            val gateway = FakeGateway(GatewayCodeResult.Sent("req"))
            val volna =
                orThrow {
                    val now = Clock.System.now()
                    ClientAppFixture.client(ClientAppFixture.org("Без Gateway"), phone, invitedAt = now)
                    ClientAppFixture.client(ClientAppFixture.org("Кабинет выключен", cabinet = false, gateway = true), phone, invitedAt = now)
                    val volna = ClientAppFixture.org("Волна", gateway = true)
                    ClientAppFixture.client(volna, phone, invitedAt = now - 1.days)
                    ClientAppFixture.client(ClientAppFixture.org("Старая", gateway = true), phone, invitedAt = now - 2.days)
                    volna
                }

            assertEquals("TELEGRAM", deliver(listOf(TelegramVerificationCodeSender(TestPostgres.db, gateway))))
            assertEquals(listOf(volna), gateway.orgs)
        }

    @Test
    fun `Gateway не доставил — код уходит по SMS-каналу пригласившей организации с её названием`() =
        runTest {
            val transports = FakeTransports()
            orThrow { ClientAppFixture.client(ClientAppFixture.org("Динамо", gateway = true, sms = true), phone) }

            val channel =
                deliver(
                    listOf(
                        TelegramVerificationCodeSender(TestPostgres.db, FakeGateway(GatewayCodeResult.Undeliverable)),
                        SmsVerificationCodeSender(TestPostgres.db, transports),
                    ),
                )

            assertEquals("SMS", channel)
            val (address, body) = transports.sent.single()
            assertEquals("+79031234567", address)
            assertTrue(body.startsWith("Динамо:"), body)
        }

    @Test
    fun `только SMS-канал — в Telegram отправка не выполняется`() =
        runTest {
            val gateway = FakeGateway(GatewayCodeResult.Sent("req"))
            orThrow { ClientAppFixture.client(ClientAppFixture.org("Динамо", sms = true), phone) }

            val channel = deliver(listOf(TelegramVerificationCodeSender(TestPostgres.db, gateway), SmsVerificationCodeSender(TestPostgres.db, FakeTransports())))

            assertEquals("SMS", channel)
            assertTrue(gateway.orgs.isEmpty())
        }

    @Test
    fun `у пригласившей организации нет SMS-канала — SMS не уходит за счёт чужой`() =
        runTest {
            val transports = FakeTransports()
            orThrow {
                ClientAppFixture.client(ClientAppFixture.org("Динамо"), phone)
                ClientAppFixture.org("Чужая", sms = true)
            }

            assertNull(deliver(listOf(SmsVerificationCodeSender(TestPostgres.db, transports)), viaSms = true))
            assertTrue(transports.sent.isEmpty())
        }

    @Test
    fun `отказ SMS-провайдера — код не доставлен`() =
        runTest {
            orThrow { ClientAppFixture.client(ClientAppFixture.org("Динамо", sms = true), phone) }

            assertNull(deliver(listOf(SmsVerificationCodeSender(TestPostgres.db, FakeTransports(SendResult.PermanentFailure("X", "x")))), viaSms = true))
        }

    /** Telegram Gateway, который отвечает [result] и запоминает организации-плательщики. */
    private class FakeGateway(private val result: GatewayCodeResult) : TelegramGateway {
        /** Организации, за счёт которых отправлялись коды. */
        val orgs = mutableListOf<OrgId>()

        context(ctx: RequestContext, raise: Raise<AppError>)
        override suspend fun verifiedToken(token: String): String = token

        context(ctx: RequestContext, raise: Raise<AppError>)
        override suspend fun send(phone: PhoneDigits, code: GatewayCode, ttl: GatewayCodeTtl): GatewayCodeResult {
            orgs += ctx.orgId
            return result
        }
    }

    /** Транспорты, которые отвечают [result] и запоминают отправки (адрес, текст). */
    private class FakeTransports(private val result: SendResult = SendResult.Sent("ok")) : ChannelTransports {
        /** Отправленные сообщения. */
        val sent = mutableListOf<Pair<String, String>>()

        override fun transport(config: ChannelConfig): ChannelTransport =
            object : ChannelTransport {
                override suspend fun send(address: String, body: String): SendResult {
                    sent += address to body
                    return result
                }
            }
    }
}
