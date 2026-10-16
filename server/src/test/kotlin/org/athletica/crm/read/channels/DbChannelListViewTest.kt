package org.athletica.crm.read.channels

import arrow.core.Either
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.MessagingFixture
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.channels.ChannelListResponse
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.core.messaging.toChannelConfig
import org.athletica.crm.employeeContext
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Тесты проекции списка каналов [DbChannelListView]: ссылка на бота. */
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

            assertEquals("https://t.me/club_bot", list().channels.single().botLink)
        }

    @Test
    fun `старый конфиг без имени бота читается и ссылки не даёт`() =
        runTest {
            MessagingFixture.insertIntegration(orgId, """{"type":"telegram_bot","botToken":"1:old"}""")

            val channel = list().channels.single()

            assertEquals(null, channel.botLink)
            assertEquals(ChannelConfig.TelegramBot(botToken = "1:old", botUsername = ""), channel.config)
        }

    @Test
    fun `десериализация конфига без botUsername даёт пустое имя`() {
        assertEquals(ChannelConfig.TelegramBot("1:old", ""), """{"type":"telegram_bot","botToken":"1:old"}""".toChannelConfig())
    }
}
