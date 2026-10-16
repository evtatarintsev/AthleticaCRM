package org.athletica.crm.usecases.messaging

import arrow.core.Either
import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.athletica.crm.MessagingFixture
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.SystemContexts
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.channels.DbChannelBindings
import org.athletica.crm.domain.channels.DbChannelChats
import org.athletica.crm.domain.clientcontacts.DbClientContacts
import org.athletica.crm.domain.conversations.DbConversations
import org.athletica.crm.domain.inbound.DbInboundReceipts
import org.athletica.crm.employeeContext
import org.athletica.crm.storage.Transaction
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Тесты приёма входящих, автопривязки по номеру и ручного разбора по чату. */
class InboundTest {
    private val orgId = OrgId.new()
    private val otherOrgId = OrgId.new()
    private val clientId = ClientId.new()
    private val stores = InboundStores(DbChannelBindings(), DbInboundReceipts(), DbConversations(), DbChannelChats(), DbClientContacts())
    private val employeeCtx = employeeContext(orgId = orgId)
    private val integrationId = ChannelIntegrationId.new()

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            MessagingFixture.insertOrg(orgId)
            MessagingFixture.insertOrg(otherOrgId, "Other")
            MessagingFixture.insertClient(orgId, clientId, "Иванов Пётр")
            MessagingFixture.insertIntegration(orgId, id = integrationId)
        }
    }

    /** Выполняет [block] в системном контексте организации, как поллер. */
    private suspend fun <T> system(block: suspend context(RequestContext, Transaction, Raise<DomainError>) () -> T): Either<DomainError, T> =
        either {
            TestPostgres.db.transaction {
                val ctx = SystemContexts.forOrg(orgId)
                context(ctx) { block() }
            }
        }

    private suspend fun receive(address: String, body: String): ClientId? = assertIs<Either.Right<ClientId?>>(system { receiveInbound(integrationId, address, body, stores) }).value

    private suspend fun linkByPhone(address: String, phone: String): PhoneLinkResult = assertIs<Either.Right<PhoneLinkResult>>(system { linkChatByPhone(integrationId, address, phone, stores) }).value

    private suspend fun assign(address: String, client: ClientId = clientId): Either<DomainError, Unit> =
        either {
            TestPostgres.db.transaction {
                context(employeeCtx) { assignChat(integrationId, address, client, stores) }
            }
        }

    @Test
    fun `входящее привязанного чата попадает в беседу, непривязанного — в очередь`() =
        runTest {
            MessagingFixture.insertBinding(orgId, integrationId, clientId, "chat-linked")

            assertEquals(clientId, receive("chat-linked", "Добрый день, можно перенести занятие?"))
            assertNull(receive("chat-new", "Привет"))

            assertEquals(listOf("Добрый день, можно перенести занятие?"), MessagingFixture.inboundBodies(clientId))
            assertEquals(1L, MessagingFixture.unresolvedCount(integrationId))
        }

    @Test
    fun `привязка переносит накопленные сообщения в порядке получения`() =
        runTest {
            receive("chat-1", "первое")
            receive("chat-1", "второе")
            receive("chat-1", "третье")

            assertIs<Either.Right<Unit>>(system { linkChat(integrationId, "chat-1", clientId, stores) })

            assertEquals(listOf("первое", "второе", "третье"), MessagingFixture.inboundBodies(clientId))
            assertEquals(0L, MessagingFixture.unresolvedCount(integrationId))
        }

    @Test
    fun `однозначное совпадение номера привязывает чат`() =
        runTest {
            MessagingFixture.insertPhone(orgId, clientId, "+79001234567")
            receive("chat-1", "Здравствуйте")

            val result = linkByPhone("chat-1", "8 (900) 123-45-67")

            assertEquals(PhoneLinkResult.Linked(clientId), result)
            assertEquals(listOf("Здравствуйте"), MessagingFixture.inboundBodies(clientId))
            assertEquals(clientId.toString(), MessagingFixture.boundClient(integrationId, "chat-1"))
        }

    @Test
    fun `номер у нескольких клиентов не привязывает чат`() =
        runTest {
            val second = MessagingFixture.insertClient(orgId, name = "Иванова Анна")
            MessagingFixture.insertPhone(orgId, clientId, "+7 900 123 45 67")
            MessagingFixture.insertPhone(orgId, second, "89001234567")

            assertEquals(PhoneLinkResult.NotMatched, linkByPhone("chat-1", "+79001234567"))
            assertNull(MessagingFixture.boundClient(integrationId, "chat-1"))
        }

    @Test
    fun `ненайденный номер не привязывает чат`() =
        runTest {
            MessagingFixture.insertPhone(orgId, clientId, "+79001234567")

            assertEquals(PhoneLinkResult.NotMatched, linkByPhone("chat-1", "+79990000000"))
            assertNull(MessagingFixture.boundClient(integrationId, "chat-1"))
        }

    @Test
    fun `клиент другой организации с этим номером не подходит`() =
        runTest {
            val foreign = MessagingFixture.insertClient(otherOrgId)
            MessagingFixture.insertPhone(otherOrgId, foreign, "+79001234567")

            assertEquals(PhoneLinkResult.NotMatched, linkByPhone("chat-1", "+79001234567"))
            assertNull(MessagingFixture.boundClient(integrationId, "chat-1"))
        }

    @Test
    fun `ручная привязка переносит сообщения, а следующее идёт в беседу само`() =
        runTest {
            receive("chat-1", "первое")
            receive("chat-1", "второе")

            assertIs<Either.Right<Unit>>(assign("chat-1"))
            assertEquals(0L, MessagingFixture.unresolvedCount(integrationId))

            assertEquals(clientId, receive("chat-1", "третье"))
            assertEquals(listOf("первое", "второе", "третье"), MessagingFixture.inboundBodies(clientId))
        }

    @Test
    fun `одновременная привязка одного чата пропускает только одну`() =
        runTest {
            val other = MessagingFixture.insertClient(orgId, name = "Сидоров")
            receive("chat-1", "Привет")

            val results =
                withContext(Dispatchers.IO) {
                    listOf(clientId, other).map { client -> async { assign("chat-1", client) } }.awaitAll()
                }

            assertEquals(1, results.count { it is Either.Right })
            val error = assertIs<Either.Left<DomainError>>(results.single { it is Either.Left }).value
            assertEquals("CHAT_ALREADY_LINKED", error.code)
            val bound = MessagingFixture.boundClient(integrationId, "chat-1")
            val winner = listOf(clientId, other).single { it.toString() == bound }
            assertEquals(listOf("Привет"), MessagingFixture.inboundBodies(winner))
        }

    @Test
    fun `второй чат того же канала привязывается к клиенту с чатом`() =
        runTest {
            MessagingFixture.insertBinding(orgId, integrationId, clientId, "chat-1")
            receive("chat-2", "С другого телефона")

            assertIs<Either.Right<Unit>>(assign("chat-2"))

            assertEquals(clientId.toString(), MessagingFixture.boundClient(integrationId, "chat-2"))
            assertEquals(clientId.toString(), MessagingFixture.boundClient(integrationId, "chat-1"))
        }

    @Test
    fun `привязка уже привязанного чата отклоняется`() =
        runTest {
            MessagingFixture.insertBinding(orgId, integrationId, clientId, "chat-1")
            val other = MessagingFixture.insertClient(orgId, name = "Сидоров")

            val error = assertIs<Either.Left<DomainError>>(assign("chat-1", other)).value

            assertEquals("CHAT_ALREADY_LINKED", error.code)
        }

    @Test
    fun `неизвестный чат или чужой клиент дают NOT_FOUND`() =
        runTest {
            val foreign = MessagingFixture.insertClient(otherOrgId)
            receive("chat-1", "Привет")

            assertEquals("NOT_FOUND", assertIs<Either.Left<DomainError>>(assign("chat-unknown")).value.code)
            assertEquals("NOT_FOUND", assertIs<Either.Left<DomainError>>(assign("chat-1", foreign)).value.code)
        }
}
