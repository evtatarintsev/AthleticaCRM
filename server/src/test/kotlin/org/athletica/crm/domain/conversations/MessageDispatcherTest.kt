package org.athletica.crm.domain.conversations

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.context.either
import arrow.core.right
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.domain.messagedelivery.ChannelRegistry
import org.athletica.crm.domain.messagedelivery.ChannelSendRequest
import org.athletica.crm.domain.messagedelivery.DbDeliveries
import org.athletica.crm.domain.messagedelivery.MessageChannel
import org.athletica.crm.domain.messagedelivery.MessageDispatcher
import org.athletica.crm.domain.messagedelivery.ProviderMessageRef
import org.athletica.crm.domain.messagedelivery.SendError
import org.athletica.crm.storage.asInt
import org.athletica.crm.storage.asString
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/** Тесты фонового диспетчера исходящих доставок ([MessageDispatcher]). */
class MessageDispatcherTest {
    private val orgId = OrgId.new()
    private val userId = UserId.new()
    private val employeeId = EmployeeId.new()
    private val branchId = BranchId.new()
    private val clientId = ClientId.new()
    private val integrationId = ChannelIntegrationId.new()

    private val conversations = DbConversations()
    private val deliveries = DbDeliveries()
    private val channels = org.athletica.crm.domain.channels.DbChannelIntegrations()

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

    /** Реестр-заглушка с настраиваемым поведением отправки. */
    private class FakeRegistry(
        private val behavior: () -> Either<SendError, ProviderMessageRef>,
    ) : ChannelRegistry {
        override fun resolve(config: ChannelConfig): MessageChannel =
            object : MessageChannel {
                override suspend fun send(request: ChannelSendRequest): Either<SendError, ProviderMessageRef> = behavior()
            }
    }

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
                VALUES (:id, :orgId, 'SMS', 'Test SMS', :config::jsonb, true)
                """.trimIndent(),
            ).bind("id", integrationId).bind("orgId", orgId).bind("config", TWILIO_CONFIG).execute()
        }
    }

    private suspend fun enqueueDelivery() {
        either {
            TestPostgres.db.transaction {
                context(ctx) {
                    val conversation = conversations.forClient(clientId)
                    val message = conversation.appendOutbound(Author.Employee(employeeId), "Привет")
                    deliveries.create(message.id, integrationId, "+79990001122")
                }
            }
        }.let { if (it is Either.Left) fail("Не удалось поставить доставку: ${it.value}") }
    }

    private suspend fun state(): String =
        TestPostgres.db.sql("SELECT state FROM message_deliveries WHERE org_id = :orgId LIMIT 1")
            .bind("orgId", orgId)
            .firstOrNull { row -> row.asString("state") }!!

    private suspend fun attempts(): Int =
        TestPostgres.db.sql("SELECT attempts FROM message_deliveries WHERE org_id = :orgId LIMIT 1")
            .bind("orgId", orgId)
            .firstOrNull { row -> row.asInt("attempts") }!!

    private suspend fun providerRef(): String? =
        TestPostgres.db.sql(
            "SELECT COALESCE(provider_message_ref, '') AS ref FROM message_deliveries WHERE org_id = :orgId LIMIT 1",
        )
            .bind("orgId", orgId)
            .firstOrNull { row -> row.asString("ref") }
            ?.takeIf { it.isNotEmpty() }

    @Test
    fun `успешная отправка переводит доставку в SENT с provider ref`() =
        runTest {
            enqueueDelivery()
            val dispatcher =
                MessageDispatcher(
                    TestPostgres.db,
                    deliveries,
                    channels,
                    FakeRegistry { ProviderMessageRef("provider-123").right() },
                )

            dispatcher.pollOnce()

            assertEquals("SENT", state())
            assertEquals("provider-123", providerRef())
        }

    @Test
    fun `временная ошибка увеличивает счётчик попыток и оставляет в PENDING`() =
        runTest {
            enqueueDelivery()
            val dispatcher =
                MessageDispatcher(
                    TestPostgres.db,
                    deliveries,
                    channels,
                    FakeRegistry { SendError.Transient("TIMEOUT", "Таймаут").left() },
                    maxRetries = 5,
                )

            dispatcher.pollOnce()

            assertEquals("PENDING", state())
            assertEquals(1, attempts())
        }

    @Test
    fun `постоянная ошибка переводит доставку в FAILED`() =
        runTest {
            enqueueDelivery()
            val dispatcher =
                MessageDispatcher(
                    TestPostgres.db,
                    deliveries,
                    channels,
                    FakeRegistry { SendError.Permanent("INVALID_NUMBER", "Неверный номер").left() },
                )

            dispatcher.pollOnce()

            assertEquals("FAILED", state())
        }

    @Test
    fun `доставка отключённой интеграции не поллится и остаётся PENDING`() =
        runTest {
            enqueueDelivery()
            TestPostgres.db.sql("UPDATE channel_integrations SET enabled = false WHERE id = :id")
                .bind("id", integrationId).execute()
            val dispatcher =
                MessageDispatcher(
                    TestPostgres.db,
                    deliveries,
                    channels,
                    FakeRegistry { ProviderMessageRef("provider-123").right() },
                )

            dispatcher.pollOnce()

            assertEquals("PENDING", state())
        }

    private companion object {
        const val TWILIO_CONFIG = """{"type":"twilio_sms","accountSid":"sid","authToken":"tok","from":"+10000000000"}"""
    }
}
