package org.athletica.crm.read.channels

import arrow.core.Either
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.MessagingFixture
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.appJson
import org.athletica.crm.api.schemas.channels.ChannelListResponse
import org.athletica.crm.api.schemas.channels.ChannelSettingsSchema
import org.athletica.crm.api.schemas.channels.ChannelShareSchema
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.core.messaging.toChannelConfig
import org.athletica.crm.employeeContext
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/** Тесты проекции списка каналов [DbChannelListView]: маски секретов и ссылка на бота. */
class DbChannelListViewTest {
    private val orgId = OrgId.new()

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking { MessagingFixture.insertOrg(orgId) }
    }

    private suspend fun list(): ChannelListResponse =
        assertIs<Either.Right<ChannelListResponse>>(
            either { TestPostgres.db.transaction { context(employeeContext(orgId = orgId)) { DbChannelListView().list() } } },
        ).value

    @Test
    fun `проверенный бот получает ссылку на t me`() =
        runTest {
            MessagingFixture.insertIntegration(orgId, MessagingFixture.telegramConfig(username = "club_bot"))

            assertEquals(ChannelShareSchema("https://t.me/club_bot"), list().channels.single().share)
        }

    @Test
    fun `выключенный бот тоже получает ссылку`() =
        runTest {
            MessagingFixture.insertIntegration(orgId, MessagingFixture.telegramConfig(username = "club_bot"), enabled = false)

            assertEquals(ChannelShareSchema("https://t.me/club_bot"), list().channels.single().share)
        }

    @Test
    fun `старый конфиг без имени бота читается и ссылки не даёт`() =
        runTest {
            MessagingFixture.insertIntegration(orgId, """{"type":"telegram_bot","botToken":"1:old"}""")

            val channel = list().channels.single()

            assertEquals(null, channel.share)
            assertEquals(ChannelSettingsSchema.TelegramBot(tokenMask = "••••", botUsername = ""), channel.settings)
        }

    @Test
    fun `у SMS-каналов ссылки нет`() =
        runTest {
            MessagingFixture.insertIntegration(orgId, """{"type":"smsc_sms","login":"club","password":"p4ssw0rd-secret"}""")

            assertEquals(null, list().channels.single().share)
        }

    @Test
    fun `секреты в ответе только маской`() =
        runTest {
            val token = "123456789:AAH-abcdefghijklmnopqrstuvwxyz0a1B9"
            MessagingFixture.insertIntegration(orgId, MessagingFixture.telegramConfig(token = token, username = "club_bot"), name = "Бот")
            MessagingFixture.insertIntegration(orgId, """{"type":"smsc_sms","login":"club","password":"p4ssw0rd-secret"}""", name = "SMSC")
            MessagingFixture.insertIntegration(
                orgId,
                """{"type":"twilio_sms","accountSid":"AC123","authToken":"twilio-auth-token-f3c2","from":"+15550001"}""",
                name = "Twilio",
            )

            val response = list()
            val json = appJson.encodeToString(ChannelListResponse.serializer(), response)

            assertEquals(
                listOf(
                    ChannelSettingsSchema.TelegramBot(tokenMask = "••••a1B9", botUsername = "club_bot"),
                    ChannelSettingsSchema.SmscSms(login = "club"),
                    ChannelSettingsSchema.TwilioSms(accountSid = "AC123", authTokenMask = "••••f3c2", from = "+15550001"),
                ),
                response.channels.map { it.settings },
            )
            listOf(token, "p4ssw0rd-secret", "twilio-auth-token-f3c2").forEach {
                assertFalse(json.contains(it), "секрет $it попал в ответ")
            }
        }

    @Test
    fun `десериализация конфига без botUsername даёт пустое имя`() {
        assertEquals(ChannelConfig.TelegramBot("1:old", ""), """{"type":"telegram_bot","botToken":"1:old"}""".toChannelConfig())
    }
}
