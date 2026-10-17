package org.athletica.crm.usecases.channels

import arrow.core.Either
import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import arrow.core.raise.context.raise
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.MessagingFixture
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.channels.ChannelConfigChange
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.core.messaging.toChannelConfig
import org.athletica.crm.domain.channels.ChannelConfigVerifier
import org.athletica.crm.domain.channels.DbChannelBindings
import org.athletica.crm.domain.channels.DbChannelIntegrations
import org.athletica.crm.domain.clientcontacts.DbClientContacts
import org.athletica.crm.employeeContext
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asString
import org.athletica.infra.messaging.ProviderChannelTransports
import org.athletica.infra.messaging.telegram.telegramTestApi
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Тесты изменения канала [updateChannel]: слияние частичного изменения и проверка у провайдера. */
class UpdateChannelTest {
    private val orgId = OrgId.new()
    private val context = employeeContext(orgId = orgId)
    private val channels =
        DbChannelIntegrations(ProviderChannelTransports(telegramTestApi { error("Bot API не вызывается") }), DbClientContacts(), DbChannelBindings())

    /** Верификатор-заглушка: запоминает проверенные конфиги, Telegram-боту даёт имя `new_bot` или отказывает. */
    private class FakeVerifier(private val reject: Boolean = false) : ChannelConfigVerifier {
        val verified = mutableListOf<ChannelConfig>()

        context(ctx: RequestContext, raise: Raise<DomainError>)
        override suspend fun verified(config: ChannelConfig): ChannelConfig {
            verified += config
            if (reject) {
                raise(CommonDomainError("TELEGRAM_TOKEN_INVALID", "Токен не принят"))
            }
            return when (config) {
                is ChannelConfig.TelegramBot -> config.copy(botUsername = "new_bot")
                is ChannelConfig.SmscSms, is ChannelConfig.TwilioSms -> config
            }
        }
    }

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking { MessagingFixture.insertOrg(orgId) }
    }

    private suspend fun update(
        verifier: ChannelConfigVerifier,
        id: ChannelIntegrationId,
        name: String,
        change: ChannelConfigChange,
    ): Either<DomainError, Unit> =
        either {
            context(TestPostgres.db, context) {
                updateChannel(channels, verifier, id, name, change)
            }
        }

    private suspend fun stored(id: ChannelIntegrationId): Triple<String, ChannelConfig, Boolean> =
        TestPostgres.db.sql("SELECT name, config::text AS config, enabled FROM channel_integrations WHERE id = :id")
            .bind("id", id)
            .firstOrNull { row -> Triple(row.asString("name"), row.asString("config").toChannelConfig(), row.asBoolean("enabled")) }
            ?: error("интеграция не найдена")

    @Test
    fun `переименование без нового токена не обращается к провайдеру`() =
        runTest {
            val id = MessagingFixture.insertIntegration(orgId, MessagingFixture.telegramConfig(token = "1:revoked", username = "old_bot"), enabled = false)
            val verifier = FakeVerifier(reject = true)

            assertIs<Either.Right<Unit>>(update(verifier, id, "Новое имя", ChannelConfigChange.TelegramBot(botToken = null)))

            assertTrue(verifier.verified.isEmpty())
            assertEquals(Triple("Новое имя", ChannelConfig.TelegramBot("1:revoked", "old_bot"), false), stored(id))
        }

    @Test
    fun `токен равный сохранённому изменением не считается`() =
        runTest {
            val id = MessagingFixture.insertIntegration(orgId, MessagingFixture.telegramConfig(token = "1:same", username = "old_bot"))
            val verifier = FakeVerifier(reject = true)

            assertIs<Either.Right<Unit>>(update(verifier, id, "Бот", ChannelConfigChange.TelegramBot(botToken = "1:same")))

            assertTrue(verifier.verified.isEmpty())
        }

    @Test
    fun `новый токен проверяется и обновляет имя бота`() =
        runTest {
            val id = MessagingFixture.insertIntegration(orgId, MessagingFixture.telegramConfig(token = "1:old", username = "old_bot"))
            val verifier = FakeVerifier()

            assertIs<Either.Right<Unit>>(update(verifier, id, "Бот", ChannelConfigChange.TelegramBot(botToken = "2:new")))

            assertEquals(listOf<ChannelConfig>(ChannelConfig.TelegramBot("2:new", "old_bot")), verifier.verified)
            assertEquals(ChannelConfig.TelegramBot("2:new", "new_bot"), stored(id).second)
        }

    @Test
    fun `отказ провайдера не меняет канал`() =
        runTest {
            val id = MessagingFixture.insertIntegration(orgId, MessagingFixture.telegramConfig(token = "1:old", username = "old_bot"), name = "Бот")

            val result = update(FakeVerifier(reject = true), id, "Другое имя", ChannelConfigChange.TelegramBot(botToken = "2:bad"))

            assertEquals("TELEGRAM_TOKEN_INVALID", assertIs<Either.Left<DomainError>>(result).value.code)
            assertEquals(Triple("Бот", ChannelConfig.TelegramBot("1:old", "old_bot"), true), stored(id))
        }

    @Test
    fun `смена номера Twilio проверяется с сохранённым токеном`() =
        runTest {
            val id =
                MessagingFixture.insertIntegration(
                    orgId,
                    """{"type":"twilio_sms","accountSid":"AC1","authToken":"secret","from":"+1555"}""",
                )
            val verifier = FakeVerifier()

            val change = ChannelConfigChange.TwilioSms(accountSid = "AC1", authToken = null, from = "+1777")
            assertIs<Either.Right<Unit>>(update(verifier, id, "SMS", change))

            val expected = ChannelConfig.TwilioSms(accountSid = "AC1", authToken = "secret", from = "+1777")
            assertEquals(listOf<ChannelConfig>(expected), verifier.verified)
            assertEquals(expected, stored(id).second)
        }

    @Test
    fun `новый пароль SMSC заменяет сохранённый`() =
        runTest {
            val id = MessagingFixture.insertIntegration(orgId, """{"type":"smsc_sms","login":"club","password":"old"}""")

            assertIs<Either.Right<Unit>>(update(FakeVerifier(), id, "SMS", ChannelConfigChange.SmscSms(login = "club", password = "new")))

            assertEquals(ChannelConfig.SmscSms(login = "club", password = "new"), stored(id).second)
        }

    @Test
    fun `сменить провайдера нельзя`() =
        runTest {
            val id = MessagingFixture.insertIntegration(orgId, """{"type":"smsc_sms","login":"club","password":"old"}""")

            val result = update(FakeVerifier(), id, "SMS", ChannelConfigChange.TelegramBot(botToken = "1:abc"))

            assertEquals("CHANNEL_PROVIDER_MISMATCH", assertIs<Either.Left<DomainError>>(result).value.code)
        }

    @Test
    fun `канал другой организации не найден`() =
        runTest {
            val otherOrgId = OrgId.new()
            MessagingFixture.insertOrg(otherOrgId, "Other")
            val id = MessagingFixture.insertIntegration(otherOrgId)

            val result = update(FakeVerifier(), id, "Бот", ChannelConfigChange.TelegramBot(botToken = null))

            assertEquals("CHANNEL_NOT_FOUND", assertIs<Either.Left<DomainError>>(result).value.code)
        }
}
