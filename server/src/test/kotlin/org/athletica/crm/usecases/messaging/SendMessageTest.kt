package org.athletica.crm.usecases.messaging

import arrow.core.Either
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.messaging.ConversationResponse
import org.athletica.crm.api.schemas.messaging.DeliveryStateSchema
import org.athletica.crm.api.schemas.messaging.OutboundMessageSchema
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
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.channels.DbChannelIntegrations
import org.athletica.crm.domain.clientcontacts.DbClientContacts
import org.athletica.crm.domain.conversations.DbConversations
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.domain.messagedelivery.DbDeliveries
import org.athletica.crm.storage.asString
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Тесты сценария отправки сообщения ([sendMessage]). */
class SendMessageTest {
    private val orgId = OrgId.new()
    private val userId = UserId.new()
    private val employeeId = EmployeeId.new()
    private val branchId = BranchId.new()
    private val clientId = ClientId.new()
    private val smsIntegrationId = ChannelIntegrationId.new()
    private val disabledIntegrationId = ChannelIntegrationId.new()
    private val inAppIntegrationId = ChannelIntegrationId.new()

    private val channels = DbChannelIntegrations()
    private val contacts = DbClientContacts()
    private val conversations = DbConversations()
    private val deliveries = DbDeliveries()

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
            insertIntegration(smsIntegrationId, "SMS", SMS_CONFIG, enabled = true)
            insertIntegration(disabledIntegrationId, "SMS", SMS_CONFIG, enabled = false)
            insertIntegration(inAppIntegrationId, "IN_APP", IN_APP_CONFIG, enabled = true)
        }
    }

    private suspend fun insertIntegration(
        id: ChannelIntegrationId,
        channelType: String,
        config: String,
        enabled: Boolean,
    ) {
        TestPostgres.db.sql(
            """
            INSERT INTO channel_integrations (id, org_id, channel_type, name, config, enabled)
            VALUES (:id, :orgId, :channelType, :name, :config::jsonb, :enabled)
            """.trimIndent(),
        )
            .bind("id", id).bind("orgId", orgId).bind("channelType", channelType)
            .bind("name", "Test $channelType").bind("config", config).bind("enabled", enabled).execute()
    }

    private suspend fun addPhoneContact(value: String = "+79990001122"): ClientContactId {
        val id = ClientContactId.new()
        TestPostgres.db.sql(
            """
            INSERT INTO client_contacts (id, org_id, client_id, type, value)
            VALUES (:id, :orgId, :clientId, 'PHONE', :value)
            """.trimIndent(),
        ).bind("id", id).bind("orgId", orgId).bind("clientId", clientId).bind("value", value).execute()
        return id
    }

    private suspend fun deliveryState(): String? =
        TestPostgres.db.sql("SELECT state FROM message_deliveries WHERE org_id = :orgId LIMIT 1")
            .bind("orgId", orgId)
            .firstOrNull { row -> row.asString("state") }

    private suspend fun recipientAddress(): String? =
        TestPostgres.db.sql("SELECT recipient_address FROM message_deliveries WHERE org_id = :orgId LIMIT 1")
            .bind("orgId", orgId)
            .firstOrNull { row -> row.asString("recipient_address") }

    private suspend fun send(
        integrationId: ChannelIntegrationId,
        contactId: ClientContactId? = null,
    ) = either {
        TestPostgres.db.transaction {
            context(ctx) {
                sendMessage(
                    SendMessageRequest(clientId, integrationId, "Привет", contactId),
                    channels,
                    contacts,
                    conversations,
                    deliveries,
                )
            }
        }
    }

    @Test
    fun `успех создаёт сообщение и доставку в статусе PENDING`() =
        runTest {
            addPhoneContact()

            val result = send(smsIntegrationId)

            val response = assertIs<Either.Right<ConversationResponse>>(result).value
            assertEquals(1, response.messages.size)
            val message = assertIs<OutboundMessageSchema>(response.messages.first())
            assertEquals(DeliveryStateSchema.PENDING, message.deliveries.first().state)
            assertEquals("PENDING", deliveryState())
        }

    @Test
    fun `без выбранного контакта берёт первый подходящий по типу`() =
        runTest {
            addPhoneContact("+79990001122")
            addPhoneContact("+79993334455")

            val result = send(smsIntegrationId)

            assertIs<Either.Right<ConversationResponse>>(result)
            assertEquals("+79990001122", recipientAddress())
        }

    @Test
    fun `выбранный контакт определяет адрес получателя`() =
        runTest {
            addPhoneContact("+79990001122")
            val second = addPhoneContact("+79993334455")

            val result = send(smsIntegrationId, second)

            assertIs<Either.Right<ConversationResponse>>(result)
            assertEquals("+79993334455", recipientAddress())
        }

    @Test
    fun `несуществующий контакт возвращает CONTACT_NOT_FOUND`() =
        runTest {
            addPhoneContact()

            val result = send(smsIntegrationId, ClientContactId.new())

            val error = assertIs<Either.Left<DomainError>>(result).value
            assertEquals("CONTACT_NOT_FOUND", error.code)
            assertEquals(null, deliveryState())
        }

    @Test
    fun `IN_APP использует идентификатор клиента как адрес получателя`() =
        runTest {
            val result = send(inAppIntegrationId)

            assertIs<Either.Right<ConversationResponse>>(result)
            assertEquals(clientId.toString(), recipientAddress())
        }

    @Test
    fun `без контакта нужного типа возвращает CLIENT_HAS_NO_CONTACT и не создаёт доставку`() =
        runTest {
            val result = send(smsIntegrationId)

            val error = assertIs<Either.Left<DomainError>>(result).value
            assertEquals("CLIENT_HAS_NO_CONTACT", error.code)
            assertEquals(null, deliveryState())
        }

    @Test
    fun `выключенный канал возвращает CHANNEL_DISABLED`() =
        runTest {
            addPhoneContact()

            val result = send(disabledIntegrationId)

            val error = assertIs<Either.Left<DomainError>>(result).value
            assertEquals("CHANNEL_DISABLED", error.code)
            assertEquals(null, deliveryState())
        }

    private companion object {
        const val SMS_CONFIG = """{"type":"twilio_sms","accountSid":"sid","authToken":"tok","from":"+10000000000"}"""
        const val IN_APP_CONFIG = """{"type":"in_app"}"""
    }
}
