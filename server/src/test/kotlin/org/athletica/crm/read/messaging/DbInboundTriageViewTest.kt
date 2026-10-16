package org.athletica.crm.read.messaging

import arrow.core.Either
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.MessagingFixture
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.messaging.TriageChatMessagesResponse
import org.athletica.crm.api.schemas.messaging.TriageListResponse
import org.athletica.crm.core.SystemContexts
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.domain.channels.DbChannelChats
import org.athletica.crm.domain.inbound.DbInboundReceipts
import org.athletica.crm.employeeContext
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Тесты очереди разбора по чату [DbInboundTriageView] и сообщений чата [DbTriageChatView]. */
class DbInboundTriageViewTest {
    private val orgId = OrgId.new()
    private val otherOrgId = OrgId.new()
    private val integrationId = ChannelIntegrationId.new()
    private val otherIntegrationId = ChannelIntegrationId.new()
    private val receipts = DbInboundReceipts()
    private val chats = DbChannelChats()

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            MessagingFixture.insertOrg(orgId)
            MessagingFixture.insertOrg(otherOrgId, "Other")
            MessagingFixture.insertIntegration(orgId, name = "Бот клуба", id = integrationId)
            MessagingFixture.insertIntegration(otherOrgId, id = otherIntegrationId)
        }
    }

    private suspend fun record(
        org: OrgId,
        integration: ChannelIntegrationId,
        address: String,
        body: String,
    ) {
        either {
            TestPostgres.db.transaction {
                context(SystemContexts.forOrg(org)) { receipts.record(integration, address, body) }
            }
        }
    }

    private suspend fun chat(
        address: String,
        name: String,
        username: String?,
        phone: String?,
    ) {
        either {
            TestPostgres.db.transaction {
                context(SystemContexts.forOrg(orgId)) { chats.upsert(integrationId, address, name, username, phone) }
            }
        }
    }

    private suspend fun triage(): TriageListResponse =
        assertIs<Either.Right<TriageListResponse>>(
            either { TestPostgres.db.transaction { context(employeeContext(orgId = orgId)) { DbInboundTriageView().list() } } },
        ).value

    @Test
    fun `чат с тремя сообщениями даёт одну запись с числом и последним текстом`() =
        runTest {
            chat("100", "Пётр", "petr", "+79001234567")
            record(orgId, integrationId, "100", "первое")
            record(orgId, integrationId, "100", "второе")
            record(orgId, integrationId, "100", "третье")

            val item = triage().chats.single()

            assertEquals(3, item.unresolvedCount)
            assertEquals("третье", item.lastMessageBody)
            assertEquals("Бот клуба", item.channelName)
            assertEquals("Пётр", item.displayName)
            assertEquals("petr", item.username)
            assertEquals("+79001234567", item.phone)
        }

    @Test
    fun `чаты отсортированы по последнему сообщению, новые первыми`() =
        runTest {
            record(orgId, integrationId, "100", "старый")
            record(orgId, integrationId, "200", "новый")
            TestPostgres.db.sql("UPDATE inbound_receipts SET received_at = now() - interval '1 minute' WHERE from_address = '100'").execute()

            assertEquals(listOf("200", "100"), triage().chats.map { it.address })
        }

    @Test
    fun `чаты других организаций в очередь не попадают`() =
        runTest {
            record(otherOrgId, otherIntegrationId, "100", "чужое")

            assertEquals(emptyList(), triage().chats)
        }

    @Test
    fun `сообщения чата отдаются в порядке получения`() =
        runTest {
            record(orgId, integrationId, "100", "первое")
            record(orgId, integrationId, "100", "второе")
            record(orgId, integrationId, "200", "другой чат")

            val messages =
                assertIs<Either.Right<TriageChatMessagesResponse>>(
                    either {
                        TestPostgres.db.transaction {
                            context(employeeContext(orgId = orgId)) { DbTriageChatView().messages(integrationId, "100") }
                        }
                    },
                ).value.messages

            assertEquals(listOf("первое", "второе"), messages.map { it.body })
        }
}
