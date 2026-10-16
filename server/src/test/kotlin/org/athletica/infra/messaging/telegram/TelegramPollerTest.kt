package org.athletica.infra.messaging.telegram

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.athletica.crm.MessagingFixture
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.channels.ChannelChat
import org.athletica.crm.domain.channels.ChannelChats
import org.athletica.crm.domain.channels.DbChannelBindings
import org.athletica.crm.domain.channels.DbChannelChats
import org.athletica.crm.domain.clientcontacts.DbClientContacts
import org.athletica.crm.domain.conversations.DbConversations
import org.athletica.crm.domain.inbound.DbInboundReceipts
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLong
import org.athletica.crm.usecases.messaging.InboundStores
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Тесты поллера одной интеграции [TelegramPoller] с фейковым Bot API и TestPostgres. */
class TelegramPollerTest {
    private val orgId = OrgId.new()
    private val integrationId = ChannelIntegrationId.new()
    private val clientId = ClientId.new()
    private val token = "77:secret"
    private val state = TelegramPollState(leaseFor = 60.seconds)

    /** Отправленные ботом ответы: тело запроса `sendMessage`. */
    private val sent = mutableListOf<String>()

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            MessagingFixture.insertOrg(orgId)
            MessagingFixture.insertClient(orgId, clientId)
            MessagingFixture.insertIntegration(orgId, MessagingFixture.telegramConfig(token), id = integrationId)
        }
    }

    /** Чаты, у которых первый `upsert` проваливается доменной ошибкой — имитация отката транзакции. */
    private class FailingOnceChats(private val delegate: ChannelChats) : ChannelChats by delegate {
        var failures = 1

        context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
        override suspend fun upsert(
            integrationId: ChannelIntegrationId,
            address: String,
            displayName: String,
            username: String?,
            phone: String?,
        ): ChannelChat {
            if (failures > 0) {
                failures--
                raise(CommonDomainError("TEST_FAILURE", "сбой"))
            }
            return delegate.upsert(integrationId, address, displayName, username, phone)
        }
    }

    private fun stores(chats: ChannelChats = DbChannelChats()) = InboundStores(DbChannelBindings(), DbInboundReceipts(), DbConversations(), chats, DbClientContacts())

    /**
     * Поллер с Bot API, который на `getUpdates` отдаёт обновления из [updates] с `update_id >= offset`;
     * [onGetUpdates] вызывается перед ответом.
     */
    private fun poller(
        updates: List<String>,
        chats: ChannelChats = DbChannelChats(),
        onGetUpdates: suspend () -> Unit = {},
    ): TelegramPoller {
        val api =
            telegramTestApi { request ->
                val body = (request.body as TextContent).text
                when (request.telegramMethod) {
                    "getUpdates" -> {
                        onGetUpdates()
                        val offset = Json.parseToJsonElement(body).jsonObject.getValue("offset").jsonPrimitive.long
                        val pending =
                            updates.filter { Json.parseToJsonElement(it).jsonObject.getValue("update_id").jsonPrimitive.long >= offset }
                        respondJson("""{"ok":true,"result":[${pending.joinToString(",")}]}""")
                    }
                    "sendMessage" -> {
                        sent += body
                        respondJson("""{"ok":true,"result":{"message_id":1,"date":0,"chat":{"id":5,"type":"private"}}}""")
                    }
                    else -> respondJson("""{"ok":true,"result":true}""")
                }
            }
        return TelegramPoller(PolledIntegration(integrationId, orgId, token), api, TestPostgres.db, state, stores(chats), owner = "test")
    }

    private fun privateUpdate(id: Long, fields: String): String =
        """{"update_id":$id,"message":{"message_id":$id,"date":0,"from":{"id":5,"is_bot":false,"first_name":"Пётр"},
           "chat":{"id":5,"type":"private"},$fields}}"""

    private suspend fun offset(): Long =
        TestPostgres.db.sql("SELECT update_offset FROM telegram_poll_state WHERE integration_id = :id")
            .bind("id", integrationId).firstOrNull { it.asLong("update_offset") } ?: 0L

    @Test
    fun `сбой транзакции не сдвигает offset, повторная выдача обрабатывается один раз`() =
        runTest {
            val chats = FailingOnceChats(DbChannelChats())
            val poller = poller(listOf(privateUpdate(10, """"text":"Привет"""")), chats)

            assertFalse(poller.pollOnce())
            assertEquals(0L, offset())
            assertEquals(0L, MessagingFixture.unresolvedCount(integrationId))

            assertTrue(poller.pollOnce())
            assertTrue(poller.pollOnce())

            assertEquals(11L, offset())
            assertEquals(1L, MessagingFixture.unresolvedCount(integrationId))
        }

    @Test
    fun `сообщения из группы игнорируются, а offset сдвигается`() =
        runTest {
            val group = """{"update_id":20,"message":{"message_id":1,"date":0,"from":{"id":5,"first_name":"Пётр"},"chat":{"id":-100,"type":"group"},"text":"всем"}}"""

            assertTrue(poller(listOf(group)).pollOnce())

            assertEquals(21L, offset())
            assertEquals(0L, MessagingFixture.unresolvedCount(integrationId))
            assertTrue(sent.isEmpty())
        }

    @Test
    fun `start даёт приветствие с запросом контакта, а свой номер привязывает чат`() =
        runTest {
            MessagingFixture.insertPhone(orgId, clientId, "+79001234567")
            val updates =
                listOf(
                    privateUpdate(1, """"text":"/start""""),
                    privateUpdate(2, """"text":"Можно записаться?""""),
                    privateUpdate(3, """"contact":{"phone_number":"89001234567","first_name":"Пётр","user_id":5}"""),
                )

            assertTrue(poller(updates).pollOnce())

            assertEquals(listOf("Можно записаться?"), MessagingFixture.inboundBodies(clientId))
            assertEquals(clientId.toString(), MessagingFixture.boundClient(integrationId, "5"))
            assertEquals(3, sent.size)
            assertTrue(sent[0].contains("\"request_contact\":true"))
            assertTrue(sent[2].contains("\"remove_keyboard\":true"))
        }

    @Test
    fun `запись с чужой арендой ничего не пишет`() =
        runTest {
            val stealLease: suspend () -> Unit = {
                TestPostgres.db.sql(
                    "UPDATE telegram_poll_state SET lease_owner = 'other', lease_until = now() + interval '60 seconds' WHERE integration_id = :id",
                ).bind("id", integrationId).execute()
            }

            assertFalse(poller(listOf(privateUpdate(30, """"text":"Привет"""")), onGetUpdates = stealLease).pollOnce())

            assertEquals(0L, offset())
            assertEquals(0L, MessagingFixture.unresolvedCount(integrationId))
        }
}
