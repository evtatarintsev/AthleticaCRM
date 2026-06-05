package org.athletica.crm.domain.conversations

import arrow.core.Either
import arrow.core.raise.context.either
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
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.employees.EmployeePermission
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/** Тесты read horizon диалога ([ConversationReadStates]). */
class ReadStateTest {
    private val orgId = OrgId.new()
    private val userId = UserId.new()
    private val employeeId = EmployeeId.new()
    private val branchId = BranchId.new()
    private val clientId = ClientId.new()
    private val integrationId = ChannelIntegrationId.new()

    private val conversations = DbConversations()
    private val readStates = DbConversationReadStates()

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
        }
    }

    @Test
    fun `markRead сдвигает указатель и обнуляет непрочитанное до новых входящих`() =
        runTest {
            val result =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) {
                            val conversation = conversations.forClient(clientId)
                            val first = conversation.appendInbound(integrationId, "Первое")

                            val before = readStates.unreadCount(conversation.id)

                            readStates.markRead(conversation.id, first.createdAt)
                            val afterRead = readStates.unreadCount(conversation.id)

                            conversation.appendInbound(integrationId, "Второе")
                            val afterNew = readStates.unreadCount(conversation.id)

                            Triple(before, afterRead, afterNew)
                        }
                    }
                }
            val (before, afterRead, afterNew) = (result as? Either.Right)?.value ?: fail("Ошибка: $result")
            assertEquals(1L, before)
            assertEquals(0L, afterRead)
            assertEquals(1L, afterNew)
        }
}
