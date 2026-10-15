package org.athletica.crm.usecases.messaging

import arrow.core.Either
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.messaging.ReceiveInboundRequest
import org.athletica.crm.api.schemas.messaging.ReceiveInboundResponse
import org.athletica.crm.api.schemas.messaging.TriageListResponse
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.ChannelBindingId
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.InboundReceiptId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.channels.DbChannelBindings
import org.athletica.crm.domain.conversations.DbConversations
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.domain.inbound.DbInboundReceipts
import org.athletica.crm.employeeContext
import org.athletica.crm.read.messaging.DbInboundTriageView
import org.athletica.crm.storage.asLong
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Тесты приёма и разбора входящих ([receiveInbound], [assignInbound]). */
class InboundTest {
    private val orgId = OrgId.new()
    private val userId = UserId.new()
    private val employeeId = EmployeeId.new()
    private val branchId = BranchId.new()
    private val clientId = ClientId.new()
    private val integrationId = ChannelIntegrationId.new()

    private val bindings = DbChannelBindings()
    private val receipts = DbInboundReceipts()
    private val conversations = DbConversations()
    private val triageView = DbInboundTriageView()

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
                INSERT INTO channel_integrations (id, org_id, name, config, enabled)
                VALUES (:id, :orgId, 'Бот', :config::jsonb, true)
                """.trimIndent(),
            ).bind("id", integrationId).bind("orgId", orgId).bind("config", TELEGRAM_CONFIG).execute()
        }
    }

    private suspend fun receive(fromAddress: String, body: String) =
        either {
            TestPostgres.db.transaction {
                context(ctx) {
                    receiveInbound(ReceiveInboundRequest(integrationId, fromAddress, body), bindings, receipts, conversations)
                }
            }
        }

    private suspend fun assign(receiptId: InboundReceiptId) =
        either {
            TestPostgres.db.transaction {
                context(ctx) {
                    assignInbound(receiptId, clientId, bindings, receipts, conversations)
                }
            }
        }

    private suspend fun linkBinding(address: String) {
        TestPostgres.db.sql(
            """
            INSERT INTO channel_bindings (id, org_id, integration_id, client_id, address)
            VALUES (:id, :orgId, :integrationId, :clientId, :address)
            """.trimIndent(),
        )
            .bind("id", ChannelBindingId.new()).bind("orgId", orgId).bind("integrationId", integrationId)
            .bind("clientId", clientId).bind("address", address).execute()
    }

    private suspend fun inboundMessageCount(): Long =
        TestPostgres.db.sql("SELECT count(*) AS c FROM messages WHERE org_id = :orgId AND direction = 'INBOUND'")
            .bind("orgId", orgId).firstOrNull { row -> row.asLong("c") } ?: 0L

    private suspend fun unresolvedCount(): Long {
        val triage =
            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        triageView.list()
                    }
                }
            }
        return assertIs<Either.Right<TriageListResponse>>(triage).value.receipts.size.toLong()
    }

    private suspend fun bindingCount(): Long =
        TestPostgres.db.sql("SELECT count(*) AS c FROM channel_bindings WHERE integration_id = :id")
            .bind("id", integrationId).firstOrNull { row -> row.asLong("c") } ?: 0L

    @Test
    fun `без связки входящее уходит в триаж и не создаёт сообщение`() =
        runTest {
            val result = receive("chat-1", "Привет")

            val response = assertIs<Either.Right<ReceiveInboundResponse>>(result).value
            assertEquals(null, response.resolvedClientId)
            assertEquals(0L, inboundMessageCount())
            assertEquals(1L, unresolvedCount())
        }

    @Test
    fun `существующая связка привязывает входящее к беседе автоматически`() =
        runTest {
            linkBinding("chat-2")

            val result = receive("chat-2", "Здравствуйте")

            val response = assertIs<Either.Right<ReceiveInboundResponse>>(result).value
            assertEquals(clientId, response.resolvedClientId)
            assertEquals(1L, inboundMessageCount())
            assertEquals(0L, unresolvedCount())
        }

    @Test
    fun `разбор создаёт связку, а следующее сообщение с адреса резолвится само`() =
        runTest {
            val first = assertIs<Either.Right<ReceiveInboundResponse>>(receive("chat-3", "Первое")).value

            assign(first.receiptId)

            assertEquals(1L, inboundMessageCount())
            assertEquals(0L, unresolvedCount())
            assertEquals(1L, bindingCount())

            val second = assertIs<Either.Right<ReceiveInboundResponse>>(receive("chat-3", "Второе")).value
            assertEquals(clientId, second.resolvedClientId)
            assertEquals(2L, inboundMessageCount())
        }

    private companion object {
        const val TELEGRAM_CONFIG = """{"type":"telegram_bot","botToken":"tok"}"""
    }
}
