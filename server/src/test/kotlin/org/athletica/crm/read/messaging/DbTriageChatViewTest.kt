package org.athletica.crm.read.messaging

import arrow.core.Either
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.MessagingFixture
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.messaging.TriageChatMessagesResponse
import org.athletica.crm.core.SystemContexts
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.domain.inbound.DbInboundReceipts
import org.athletica.crm.employeeContext
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Тесты сообщений неразобранного чата [DbTriageChatView]. */
class DbTriageChatViewTest {
    private val orgId = OrgId.new()
    private val integrationId = ChannelIntegrationId.new()
    private val receipts = DbInboundReceipts()

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            MessagingFixture.insertOrg(orgId)
            MessagingFixture.insertIntegration(orgId, name = "Бот клуба", id = integrationId)
        }
    }

    private suspend fun record(
        address: String,
        body: String,
    ) {
        either {
            TestPostgres.db.transaction {
                context(SystemContexts.forOrg(orgId)) { receipts.record(integrationId, address, body) }
            }
        }
    }

    @Test
    fun `сообщения чата отдаются в порядке получения`() =
        runTest {
            record("100", "первое")
            record("100", "второе")
            record("200", "другой чат")

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
