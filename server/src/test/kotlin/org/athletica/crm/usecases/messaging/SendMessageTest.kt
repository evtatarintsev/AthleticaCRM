package org.athletica.crm.usecases.messaging

import arrow.core.Either
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.messaging.SendMessageRequest
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ClientContactId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.messaging.MessageStatus
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.channels.DbChannelIntegrations
import org.athletica.crm.domain.clientcontacts.DbClientContacts
import org.athletica.crm.domain.conversations.DbConversations
import org.athletica.crm.domain.conversations.DbMessages
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.storage.asString
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Тесты сценария постановки сообщения в очередь ([sendMessage]). */
class SendMessageTest {
    private val orgId = OrgId.new()
    private val userId = UserId.new()
    private val employeeId = EmployeeId.new()
    private val branchId = BranchId.new()
    private val clientId = ClientId.new()
    private val smsIntegrationId = ChannelIntegrationId.new()
    private val disabledIntegrationId = ChannelIntegrationId.new()

    private val channels = DbChannelIntegrations()
    private val contacts = DbClientContacts()
    private val conversations = DbConversations()
    private val messages = DbMessages()

    private val ctx =
        EmployeeRequestContext(
            lang = Lang.RU,
            userId = userId,
            orgId = orgId,
            branchId = branchId,
            employeeId = employeeId,
            username = "user@example.com",
            clientIp = null,
            currency = Currency.RUB,
            permission = EmployeePermission(),
        )

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .bind("id", orgId).bind("name", "Org").execute()
            TestPostgres.db.sql("INSERT INTO users (id, login, password_hash) VALUES (:id, :login, :hash)")
                .bind("id", userId).bind("login", "a@example.com").bind("hash", "h").execute()
            TestPostgres.db.sql("INSERT INTO employees (id, org_id, name) VALUES (:id, :orgId, :name)")
                .bind("id", employeeId).bind("orgId", orgId).bind("name", "Админ").execute()
            TestPostgres.db.sql("INSERT INTO clients (id, org_id, name, gender) VALUES (:id, :orgId, :name, 'MALE'::gender)")
                .bind("id", clientId).bind("orgId", orgId).bind("name", "Клиент").execute()
            TestPostgres.db.sql(
                """
                INSERT INTO channel_integrations (id, org_id, channel_type, name, config, enabled)
                VALUES (:id, :orgId, 'SMS', 'Test SMS', '{}'::jsonb, true)
                """.trimIndent(),
            ).bind("id", smsIntegrationId).bind("orgId", orgId).execute()
            TestPostgres.db.sql(
                """
                INSERT INTO channel_integrations (id, org_id, channel_type, name, config, enabled)
                VALUES (:id, :orgId, 'SMS', 'Disabled SMS', '{}'::jsonb, false)
                """.trimIndent(),
            ).bind("id", disabledIntegrationId).bind("orgId", orgId).execute()
        }
    }

    private suspend fun addSmsContact() {
        TestPostgres.db.sql(
            """
            INSERT INTO client_contacts (id, org_id, client_id, channel_type, address)
            VALUES (:id, :orgId, :clientId, 'SMS', '+79990001122')
            """.trimIndent(),
        ).bind("id", ClientContactId.new()).bind("orgId", orgId).bind("clientId", clientId).execute()
    }

    private suspend fun messageStatus(): String? =
        TestPostgres.db.sql("SELECT status FROM messages WHERE org_id = :orgId LIMIT 1")
            .bind("orgId", orgId)
            .firstOrNull { row -> row.asString("status") }

    private suspend fun send(integrationId: ChannelIntegrationId) =
        either<DomainError, _> {
            TestPostgres.db.transaction {
                context(ctx, this) {
                    sendMessage(
                        SendMessageRequest(clientId, integrationId, "Привет"),
                        channels,
                        contacts,
                        conversations,
                        messages,
                    )
                }
            }
        }

    @Test
    fun `успех ставит сообщение в очередь QUEUED`() =
        runTest {
            addSmsContact()

            val result = send(smsIntegrationId)

            val response = assertIs<Either.Right<*>>(result).value as org.athletica.crm.api.schemas.messaging.ConversationResponse
            assertEquals(1, response.messages.size)
            assertEquals(MessageStatus.QUEUED, response.messages.first().status)
            assertEquals("QUEUED", messageStatus())
        }

    @Test
    fun `без контакта нужного типа возвращает CLIENT_HAS_NO_CONTACT и не создаёт сообщение`() =
        runTest {
            val result = send(smsIntegrationId)

            val error = assertIs<Either.Left<DomainError>>(result).value
            assertEquals("CLIENT_HAS_NO_CONTACT", error.code)
            assertEquals(null, messageStatus())
        }

    @Test
    fun `выключенный канал возвращает CHANNEL_DISABLED`() =
        runTest {
            addSmsContact()

            val result = send(disabledIntegrationId)

            val error = assertIs<Either.Left<DomainError>>(result).value
            assertEquals("CHANNEL_DISABLED", error.code)
            assertEquals(null, messageStatus())
        }
}
