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
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.messaging.ChannelType
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.storage.asInt
import org.athletica.crm.storage.asString
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/** Тесты фонового диспетчера исходящих сообщений ([MessageDispatcher]). */
class MessageDispatcherTest {
    private val orgId = OrgId.new()
    private val userId = UserId.new()
    private val employeeId = EmployeeId.new()
    private val branchId = BranchId.new()
    private val clientId = ClientId.new()
    private val integrationId = ChannelIntegrationId.new()

    private val conversations = DbConversations()
    private val messages = DbMessages()
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
        override fun resolve(channelType: ChannelType, config: Map<String, String>): MessageChannel =
            object : MessageChannel {
                override val type = channelType

                override suspend fun send(message: OutboundMessage): Either<SendError, ProviderMessageRef> = behavior()
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
                VALUES (:id, :orgId, 'SMS', 'Test SMS', '{}'::jsonb, true)
                """.trimIndent(),
            ).bind("id", integrationId).bind("orgId", orgId).execute()
        }
    }

    private suspend fun enqueueMessage() {
        either<DomainError, Unit> {
            TestPostgres.db.transaction {
                context(ctx, this) {
                    val conversation = conversations.forClient(clientId)
                    messages.enqueue(conversation.id, integrationId, ChannelType.SMS, "+79990001122", "Привет")
                }
            }
        }.let { if (it is Either.Left) fail("Не удалось поставить сообщение: ${it.value}") }
    }

    private suspend fun status(): String =
        TestPostgres.db.sql("SELECT status FROM messages WHERE org_id = :orgId LIMIT 1")
            .bind("orgId", orgId)
            .firstOrNull { row -> row.asString("status") }!!

    private suspend fun retryCount(): Int =
        TestPostgres.db.sql("SELECT retry_count FROM messages WHERE org_id = :orgId LIMIT 1")
            .bind("orgId", orgId)
            .firstOrNull { row -> row.asInt("retry_count") }!!

    private suspend fun providerRef(): String? =
        TestPostgres.db.sql("SELECT COALESCE(provider_message_ref, '') AS ref FROM messages WHERE org_id = :orgId LIMIT 1")
            .bind("orgId", orgId)
            .firstOrNull { row -> row.asString("ref") }
            ?.takeIf { it.isNotEmpty() }

    @Test
    fun `успешная отправка переводит сообщение в SENT с provider ref`() =
        runTest {
            enqueueMessage()
            val dispatcher =
                MessageDispatcher(
                    TestPostgres.db,
                    messages,
                    channels,
                    FakeRegistry { ProviderMessageRef("provider-123").right() },
                )

            dispatcher.pollOnce()

            assertEquals("SENT", status())
            assertEquals("provider-123", providerRef())
        }

    @Test
    fun `временная ошибка увеличивает счётчик попыток и оставляет в QUEUED`() =
        runTest {
            enqueueMessage()
            val dispatcher =
                MessageDispatcher(
                    TestPostgres.db,
                    messages,
                    channels,
                    FakeRegistry { SendError.Transient("TIMEOUT", "Таймаут").left() },
                    maxRetries = 5,
                )

            dispatcher.pollOnce()

            assertEquals("QUEUED", status())
            assertEquals(1, retryCount())
        }

    @Test
    fun `постоянная ошибка переводит сообщение в FAILED`() =
        runTest {
            enqueueMessage()
            val dispatcher =
                MessageDispatcher(
                    TestPostgres.db,
                    messages,
                    channels,
                    FakeRegistry { SendError.Permanent("INVALID_NUMBER", "Неверный номер").left() },
                )

            dispatcher.pollOnce()

            assertEquals("FAILED", status())
        }
}
