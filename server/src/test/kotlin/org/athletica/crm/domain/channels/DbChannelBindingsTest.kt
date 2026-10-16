package org.athletica.crm.domain.channels

import arrow.core.Either
import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.MessagingFixture
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.SystemContexts
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Тесты хранилища связок «адрес чата ↔ клиент» [DbChannelBindings]. */
class DbChannelBindingsTest {
    private val orgId = OrgId.new()
    private val integrationId = ChannelIntegrationId.new()
    private val clientId = ClientId.new()
    private val otherClientId = ClientId.new()
    private val bindings = DbChannelBindings()

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            MessagingFixture.insertOrg(orgId)
            MessagingFixture.insertClient(orgId, clientId)
            MessagingFixture.insertClient(orgId, otherClientId, "Другой")
            MessagingFixture.insertIntegration(orgId, id = integrationId)
        }
    }

    private suspend fun <T> inOrg(block: suspend context(RequestContext, Transaction, Raise<DomainError>) () -> T): Either<DomainError, T> =
        either {
            TestPostgres.db.transaction {
                context(SystemContexts.forOrg(orgId)) { block() }
            }
        }

    @Test
    fun `повторная привязка адреса даёт CHAT_ALREADY_LINKED и не перепривязывает`() =
        runTest {
            assertIs<Either.Right<ChannelBinding>>(inOrg { bindings.link(integrationId, clientId, "chat-1") })

            val result = inOrg { bindings.link(integrationId, otherClientId, "chat-1") }

            assertEquals("CHAT_ALREADY_LINKED", assertIs<Either.Left<DomainError>>(result).value.code)
            assertEquals(clientId.toString(), MessagingFixture.boundClient(integrationId, "chat-1"))
        }

    @Test
    fun `ответ клиенту с двумя чатами уходит в последний писавший`() =
        runTest {
            inOrg {
                bindings.link(integrationId, clientId, "chat-1")
                bindings.link(integrationId, clientId, "chat-2")
            }
            inOrg { bindings.touchInbound(integrationId, "chat-1") }
            inOrg { bindings.touchInbound(integrationId, "chat-2") }

            assertEquals("chat-2", assertIs<Either.Right<String?>>(inOrg { bindings.addressFor(integrationId, clientId) }).value)

            inOrg { bindings.touchInbound(integrationId, "chat-1") }

            assertEquals("chat-1", assertIs<Either.Right<String?>>(inOrg { bindings.addressFor(integrationId, clientId) }).value)
        }
}
