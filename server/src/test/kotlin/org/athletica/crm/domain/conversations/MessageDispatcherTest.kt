package org.athletica.crm.domain.conversations

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.DeliveryId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.channels.ChannelIntegration
import org.athletica.crm.domain.channels.ChannelIntegrations
import org.athletica.crm.domain.channels.ChannelTransport
import org.athletica.crm.domain.channels.ChannelTransports
import org.athletica.crm.domain.channels.DbChannelBindings
import org.athletica.crm.domain.channels.DbChannelIntegrations
import org.athletica.crm.domain.channels.SendResult
import org.athletica.crm.domain.clientcontacts.DbClientContacts
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.domain.messagedelivery.DbDeliveries
import org.athletica.crm.domain.messagedelivery.DeliveryError
import org.athletica.crm.domain.messagedelivery.MessageDispatcher
import org.athletica.crm.domain.messagedelivery.PendingDelivery
import org.athletica.crm.employeeContext
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInt
import org.athletica.crm.storage.asString
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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

    private val ctx =
        employeeContext(
            lang = Lang.RU,
            userId = userId,
            orgId = orgId,
            branchId = branchId,
            employeeId = employeeId,
            username = "user@example.com",
            clientIp = null,
            currency = Currency.RUB,
            permissions = EmployeePermission(),
        )

    /** Интеграции с транспортом-заглушкой: каждая отправка возвращает фиксированный [result]. */
    private fun channelsWith(result: SendResult) =
        DbChannelIntegrations(
            object : ChannelTransports {
                override fun transport(config: ChannelConfig): ChannelTransport =
                    object : ChannelTransport {
                        override suspend fun send(address: String, body: String): SendResult = result
                    }
            },
            DbClientContacts(),
            DbChannelBindings(),
        )

    @Before
    fun setUp() {
        orThrow {
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
                    INSERT INTO channel_integrations (id, org_id, name, config, enabled)
                    VALUES (:id, :orgId, 'Test SMS', :config::jsonb, true)
                    """.trimIndent(),
                ).bind("id", integrationId).bind("orgId", orgId).bind("config", TWILIO_CONFIG).execute()
            }
        }
    }

    private suspend fun enqueueDelivery(): DeliveryId =
        either {
            TestPostgres.db.transaction {
                context(ctx) {
                    val conversation = conversations.forClient(clientId)
                    val message = conversation.appendOutbound(Author.Employee(employeeId), "Привет")
                    deliveries.create(message.id, integrationId) { "+79990001122" }.id
                }
            }
        }.getOrElse { fail("Не удалось поставить доставку: $it") }

    private suspend fun errorCode(): String? =
        orThrow {
            TestPostgres.db.sql(
                "SELECT COALESCE(error_code, '') AS code FROM message_deliveries WHERE org_id = :orgId LIMIT 1",
            )
                .bind("orgId", orgId)
                .firstOrNull { row -> row.asString("code") }
                ?.takeIf { it.isNotEmpty() }
        }

    private suspend fun state(): String =
        orThrow {
            TestPostgres.db.sql("SELECT state FROM message_deliveries WHERE org_id = :orgId LIMIT 1")
                .bind("orgId", orgId)
                .firstOrNull { row -> row.asString("state") }!!
        }

    private suspend fun attempts(): Int =
        orThrow {
            TestPostgres.db.sql("SELECT attempts FROM message_deliveries WHERE org_id = :orgId LIMIT 1")
                .bind("orgId", orgId)
                .firstOrNull { row -> row.asInt("attempts") }!!
        }

    private suspend fun providerRef(): String? =
        orThrow {
            TestPostgres.db.sql(
                "SELECT COALESCE(provider_message_ref, '') AS ref FROM message_deliveries WHERE org_id = :orgId LIMIT 1",
            )
                .bind("orgId", orgId)
                .firstOrNull { row -> row.asString("ref") }
                ?.takeIf { it.isNotEmpty() }
        }

    @Test
    fun `успешная отправка переводит доставку в SENT с provider ref`() =
        runTest {
            enqueueDelivery()
            val dispatcher =
                MessageDispatcher(
                    TestPostgres.db,
                    deliveries,
                    channelsWith(SendResult.Sent("provider-123")),
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
                    channelsWith(SendResult.TransientFailure("TIMEOUT", "Таймаут")),
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
                    channelsWith(SendResult.PermanentFailure("INVALID_NUMBER", "Неверный номер")),
                )

            dispatcher.pollOnce()

            assertEquals("FAILED", state())
        }

    @Test
    fun `доставка отключённой интеграции не поллится и остаётся PENDING`() =
        orThrow {
            runTest {
                enqueueDelivery()
                TestPostgres.db.sql("UPDATE channel_integrations SET enabled = false WHERE id = :id")
                    .bind("id", integrationId).execute()
                val dispatcher =
                    MessageDispatcher(
                        TestPostgres.db,
                        deliveries,
                        channelsWith(SendResult.Sent("provider-123")),
                    )

                dispatcher.pollOnce()

                assertEquals("PENDING", state())
            }
        }

    @Test
    fun `временная ошибка на последней попытке переводит доставку в FAILED`() =
        runTest {
            enqueueDelivery()
            val dispatcher =
                MessageDispatcher(
                    TestPostgres.db,
                    deliveries,
                    channelsWith(SendResult.TransientFailure("TIMEOUT", "Таймаут")),
                    maxRetries = 2,
                )

            dispatcher.pollOnce()
            dispatcher.pollOnce()

            assertEquals("FAILED", state())
            assertEquals("TIMEOUT", errorCode())
        }

    @Test
    fun `без отправителя доставка переходит в FAILED, а не остаётся PENDING`() =
        runTest {
            enqueueDelivery()
            val withoutSenders =
                object : ChannelIntegrations by channelsWith(SendResult.Sent("provider-123")) {
                    context(tr: Transaction, raise: Raise<AppError>)
                    override suspend fun senderById(id: ChannelIntegrationId): ChannelIntegration? = null
                }

            MessageDispatcher(TestPostgres.db, deliveries, withoutSenders).pollOnce()

            assertEquals("FAILED", state())
            assertEquals(PendingDelivery.CHANNEL_NOT_FOUND, errorCode())
        }

    @Test
    fun `переход разрешён только из PENDING`() =
        runTest {
            val id = enqueueDelivery()
            MessageDispatcher(TestPostgres.db, deliveries, channelsWith(SendResult.Sent("provider-123"))).pollOnce()

            val result =
                either {
                    TestPostgres.db.transaction { deliveries.markFailed(id, DeliveryError("LATE", "Поздно")) }
                }

            assertEquals("DELIVERY_NOT_PENDING", assertIs<Either.Left<AppError>>(result).value.code)
            assertEquals("SENT", state())
        }

    @Test
    fun `захваченную доставку другой обработчик пропускает`() =
        runTest {
            val id = enqueueDelivery()
            var sends = 0
            val counting =
                DbChannelIntegrations(
                    object : ChannelTransports {
                        override fun transport(config: ChannelConfig): ChannelTransport =
                            object : ChannelTransport {
                                override suspend fun send(address: String, body: String): SendResult {
                                    sends++
                                    return SendResult.Sent("provider-123")
                                }
                            }
                    },
                    DbClientContacts(),
                    DbChannelBindings(),
                )

            orThrow {
                TestPostgres.db.transaction {
                    assertNotNull(deliveries.claim(id))
                    MessageDispatcher(TestPostgres.db, deliveries, counting).pollOnce()
                }
            }

            assertEquals(0, sends)
            assertEquals("PENDING", state())
        }

    private companion object {
        const val TWILIO_CONFIG = """{"type":"twilio_sms","accountSid":"sid","authToken":"tok","from":"+10000000000"}"""
    }
}
