package org.athletica.crm.read.messaging

import arrow.core.Either
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.MessagingFixture
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.messaging.ClientConversationItemSchema
import org.athletica.crm.api.schemas.messaging.InboxCounterResponse
import org.athletica.crm.api.schemas.messaging.InboxFilterSchema
import org.athletica.crm.api.schemas.messaging.InboxItemSchema
import org.athletica.crm.api.schemas.messaging.InboxListResponse
import org.athletica.crm.api.schemas.messaging.InboxMessageDirectionSchema
import org.athletica.crm.api.schemas.messaging.UnresolvedChatItemSchema
import org.athletica.crm.core.SystemContexts
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.domain.channels.DbChannelChats
import org.athletica.crm.domain.conversations.Author
import org.athletica.crm.domain.conversations.DbConversationReadStates
import org.athletica.crm.domain.conversations.DbConversations
import org.athletica.crm.domain.inbound.DbInboundReceipts
import org.athletica.crm.employeeContext
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Тесты проекции «Входящих» [DbInboxView]. */
class DbInboxViewTest {
    private val orgId = OrgId.new()
    private val otherOrgId = OrgId.new()
    private val integrationId = ChannelIntegrationId.new()
    private val otherIntegrationId = ChannelIntegrationId.new()
    private val ivanov = ClientId.new()
    private val petrov = ClientId.new()

    private val receipts = DbInboundReceipts()
    private val chats = DbChannelChats()
    private val conversations = DbConversations()
    private val readStates = DbConversationReadStates()
    private val ctx = employeeContext(orgId = orgId)
    private val now = Clock.System.now()

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            MessagingFixture.insertOrg(orgId)
            MessagingFixture.insertOrg(otherOrgId, "Other")
            MessagingFixture.insertIntegration(orgId, name = "Бот клуба", id = integrationId)
            MessagingFixture.insertIntegration(otherOrgId, id = otherIntegrationId)
            MessagingFixture.insertClient(orgId, ivanov, "Иванов")
            MessagingFixture.insertClient(orgId, petrov, "Петров")
        }
    }

    private suspend fun inbound(
        clientId: ClientId,
        body: String,
        at: Instant,
    ) {
        either {
            TestPostgres.db.transaction {
                context(ctx) {
                    val conversation = conversations.forClient(clientId)
                    conversation.appendInbound(integrationId, body, at)
                    conversation.touch()
                }
            }
        }
    }

    private suspend fun outbound(
        clientId: ClientId,
        body: String,
    ) {
        either {
            TestPostgres.db.transaction {
                context(ctx) {
                    val conversation = conversations.forClient(clientId)
                    conversation.appendOutbound(Author.Employee(ctx.employee.id), body)
                    conversation.touch()
                }
            }
        }
    }

    private suspend fun markRead(
        clientId: ClientId,
        at: Instant,
    ) {
        either {
            TestPostgres.db.transaction {
                context(ctx) { readStates.markRead(conversations.forClient(clientId).id, at) }
            }
        }
    }

    private suspend fun unresolved(
        org: OrgId,
        integration: ChannelIntegrationId,
        address: String,
        body: String,
        at: Instant,
    ) {
        either {
            TestPostgres.db.transaction {
                context(SystemContexts.forOrg(org)) { receipts.record(integration, address, body) }
            }
        }
        TestPostgres.db.sql("UPDATE inbound_receipts SET received_at = :at WHERE from_address = :address AND body = :body")
            .bind("at", at).bind("address", address).bind("body", body).execute()
    }

    private suspend fun list(
        filter: InboxFilterSchema = InboxFilterSchema.ALL,
        limit: Int = 50,
        offset: Int = 0,
    ): InboxListResponse =
        assertIs<Either.Right<InboxListResponse>>(
            either { TestPostgres.db.transaction { context(ctx) { DbInboxView().list(InboxQuery(filter, limit, offset)) } } },
        ).value

    private suspend fun counter(): Int =
        assertIs<Either.Right<InboxCounterResponse>>(
            either { TestPostgres.db.transaction { context(ctx) { DbInboxView().counter() } } },
        ).value.count

    private fun InboxItemSchema.label(): String =
        when (this) {
            is ClientConversationItemSchema -> clientName
            is UnresolvedChatItemSchema -> "chat:$address"
        }

    @Test
    fun `беседы и неразобранные чаты сортируются вместе, свежие первыми`() =
        runTest {
            inbound(ivanov, "Привет", now - 5.minutes)
            unresolved(orgId, integrationId, "100", "Кто это?", now)
            outbound(petrov, "Ждём вас")
            TestPostgres.db.sql("UPDATE conversations SET last_message_at = :at WHERE client_id = :clientId")
                .bind("at", now - 10.minutes).bind("clientId", petrov).execute()

            assertEquals(listOf("chat:100", "Иванов", "Петров"), list().items.map { it.label() })
        }

    @Test
    fun `запись беседы показывает последнее сообщение, направление и непрочитанное`() =
        runTest {
            inbound(ivanov, "Первое", now - 3.minutes)
            inbound(ivanov, "Второе", now - 2.minutes)
            outbound(ivanov, "Ждём вас в среду")

            val item = assertIs<ClientConversationItemSchema>(list().items.single())

            assertEquals(ivanov, item.clientId)
            assertEquals("Ждём вас в среду", item.lastMessageBody)
            assertEquals(InboxMessageDirectionSchema.OUTBOUND, item.lastMessageDirection)
            assertEquals(2, item.unreadCount)
        }

    @Test
    fun `запись неразобранного чата несёт данные чата и число сообщений`() =
        runTest {
            either {
                TestPostgres.db.transaction {
                    context(SystemContexts.forOrg(orgId)) { chats.upsert(integrationId, "100", "Пётр", "petr", "+79001234567") }
                }
            }
            unresolved(orgId, integrationId, "100", "первое", now - 2.minutes)
            unresolved(orgId, integrationId, "100", "второе", now - 1.minutes)

            val item = assertIs<UnresolvedChatItemSchema>(list().items.single())

            assertEquals(2, item.unresolvedCount)
            assertEquals("второе", item.lastMessageBody)
            assertEquals("Бот клуба", item.channelName)
            assertEquals("Пётр", item.displayName)
            assertEquals("petr", item.username)
            assertEquals("+79001234567", item.phone)
        }

    @Test
    fun `клиент без сообщений и пустая беседа в список не попадают`() =
        runTest {
            either { TestPostgres.db.transaction { context(ctx) { conversations.forClient(ivanov) } } }

            assertEquals(emptyList(), list().items)
        }

    @Test
    fun `беседы и чаты других организаций в список не попадают`() =
        runTest {
            unresolved(otherOrgId, otherIntegrationId, "100", "чужое", now)

            assertEquals(emptyList(), list().items)
            assertEquals(0, counter())
        }

    @Test
    fun `фильтр UNREAD оставляет беседы с непрочитанным и неразобранные чаты`() =
        runTest {
            inbound(ivanov, "Непрочитанное", now - 1.minutes)
            inbound(petrov, "Прочитанное", now - 2.minutes)
            markRead(petrov, now - 2.minutes)
            unresolved(orgId, integrationId, "100", "Кто это?", now)

            assertEquals(listOf("chat:100", "Иванов"), list(InboxFilterSchema.UNREAD).items.map { it.label() })
            assertEquals(2, counter())
        }

    @Test
    fun `фильтр UNRESOLVED оставляет только неразобранные чаты`() =
        runTest {
            inbound(ivanov, "Привет", now - 1.minutes)
            unresolved(orgId, integrationId, "100", "Кто это?", now - 2.minutes)

            assertEquals(listOf("chat:100"), list(InboxFilterSchema.UNRESOLVED).items.map { it.label() })
        }

    @Test
    fun `пагинация отдаёт страницу и общее число`() =
        runTest {
            inbound(ivanov, "Привет", now - 1.minutes)
            inbound(petrov, "Привет", now - 2.minutes)
            unresolved(orgId, integrationId, "100", "Кто это?", now - 3.minutes)

            val page = list(limit = 1, offset = 1)

            assertEquals(3, page.total)
            assertEquals(listOf("Петров"), page.items.map { it.label() })
        }

    @Test
    fun `счётчик обнуляется, когда всё прочитано и разобрано`() =
        runTest {
            inbound(ivanov, "Привет", now - 1.minutes)
            markRead(ivanov, now)

            assertEquals(0, counter())
        }
}
