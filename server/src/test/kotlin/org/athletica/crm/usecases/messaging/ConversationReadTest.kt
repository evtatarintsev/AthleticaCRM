package org.athletica.crm.usecases.messaging

import arrow.core.Either
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.MessagingFixture
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.clients.DbClients
import org.athletica.crm.domain.conversations.DbConversationReadStates
import org.athletica.crm.domain.conversations.DbConversations
import org.athletica.crm.employeeContext
import org.athletica.crm.storage.asLong
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Тесты отметки прочтения беседы ([markConversationRead]). */
class ConversationReadTest {
    private val orgId = OrgId.new()
    private val otherOrgId = OrgId.new()
    private val clientId = ClientId.new()
    private val otherClientId = ClientId.new()
    private val integrationId = ChannelIntegrationId.new()

    private val clients = DbClients()
    private val conversations = DbConversations()
    private val readStates = DbConversationReadStates()
    private val ctx = employeeContext(orgId = orgId)

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            MessagingFixture.insertOrg(orgId)
            MessagingFixture.insertOrg(otherOrgId, "Other")
            MessagingFixture.insertClient(orgId, clientId)
            MessagingFixture.insertClient(otherOrgId, otherClientId)
            MessagingFixture.insertIntegration(orgId, id = integrationId)
        }
    }

    private suspend fun inbound(at: Instant) {
        either {
            TestPostgres.db.transaction {
                context(ctx) { conversations.forClient(clientId).appendInbound(integrationId, "Привет", at) }
            }
        }
    }

    private suspend fun markRead(
        client: ClientId,
        upTo: Instant,
    ): Either<DomainError, Unit> =
        either {
            TestPostgres.db.transaction {
                context(ctx) { markConversationRead(client, upTo, clients, conversations, readStates) }
            }
        }

    private suspend fun unread(): Long =
        assertIs<Either.Right<Long>>(
            either {
                TestPostgres.db.transaction {
                    context(ctx) { readStates.unreadCount(conversations.forClient(clientId).id) }
                }
            },
        ).value

    private suspend fun conversationCount(): Long =
        TestPostgres.db.sql("SELECT count(*) AS n FROM conversations WHERE org_id = :orgId")
            .bind("orgId", orgId)
            .firstOrNull { row -> row.asLong("n") } ?: 0L

    @Test
    fun `отметка до показанного сообщения обнуляет непрочитанное`() =
        runTest {
            val shown = Clock.System.now() - 1.minutes
            inbound(shown)

            assertIs<Either.Right<Unit>>(markRead(clientId, shown))

            assertEquals(0L, unread())
        }

    @Test
    fun `сообщение после показанного остаётся непрочитанным`() =
        runTest {
            val shown = Clock.System.now() - 2.minutes
            inbound(shown)
            inbound(shown + 1.minutes)

            markRead(clientId, shown)

            assertEquals(1L, unread())
        }

    @Test
    fun `без беседы ничего не делает и беседу не создаёт`() =
        runTest {
            assertIs<Either.Right<Unit>>(markRead(clientId, Clock.System.now()))

            assertEquals(0L, conversationCount())
        }

    @Test
    fun `клиент чужой организации даёт CLIENT_NOT_FOUND`() =
        runTest {
            val error = assertIs<Either.Left<DomainError>>(markRead(otherClientId, Clock.System.now())).value
            assertEquals("CLIENT_NOT_FOUND", error.code)
        }

    @Test
    fun `несуществующий клиент даёт CLIENT_NOT_FOUND`() =
        runTest {
            val error = assertIs<Either.Left<DomainError>>(markRead(ClientId.new(), Clock.System.now())).value
            assertEquals("CLIENT_NOT_FOUND", error.code)
        }
}
