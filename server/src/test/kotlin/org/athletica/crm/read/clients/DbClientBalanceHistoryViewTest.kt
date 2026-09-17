package org.athletica.crm.read.clients

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.clients.ClientBalanceHistoryResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.athletica.crm.domain.employees.EmployeePermission
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Тесты read-проекции журнала баланса: порядок, подстановка имени сотрудника и изоляция. */
class DbClientBalanceHistoryViewTest {
    private val view = DbClientBalanceHistoryView()
    private val baseInstant = Instant.fromEpochMilliseconds(1_700_000_000_000)

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    private suspend fun insertOrg(name: String = "Test Org"): Uuid {
        val orgId = Uuid.generateV7()
        TestPostgres.db
            .sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
            .bind("id", orgId).bind("name", name)
            .execute()
        return orgId
    }

    private suspend fun insertClient(orgId: Uuid, name: String): ClientId {
        val clientId = ClientId.new()
        TestPostgres.db
            .sql("INSERT INTO clients (id, org_id, name, gender) VALUES (:id, :orgId, :name, 'MALE'::gender)")
            .bind("id", clientId).bind("orgId", orgId).bind("name", name)
            .execute()
        return clientId
    }

    private suspend fun insertEmployee(orgId: Uuid, name: String): EmployeeId {
        val userId = UserId.new()
        val employeeId = EmployeeId.new()
        TestPostgres.db
            .sql("INSERT INTO users (id, login, password_hash) VALUES (:id, :login, :hash)")
            .bind("id", userId).bind("login", "u$userId@example.com").bind("hash", "hash")
            .execute()
        TestPostgres.db
            .sql("INSERT INTO employees (id, user_id, org_id, name) VALUES (:id, :userId, :orgId, :name)")
            .bind("id", employeeId).bind("userId", userId).bind("orgId", orgId).bind("name", name)
            .execute()
        return employeeId
    }

    private suspend fun insertEntry(
        orgId: Uuid,
        clientId: ClientId,
        performedBy: EmployeeId,
        amount: Long,
        balanceAfter: Long,
        createdAt: Instant,
        note: String? = null,
        operationType: String = "admin_credit",
    ) {
        TestPostgres.db
            .sql(
                """
                INSERT INTO client_balance_journal (org_id, client_id, amount, balance_after, operation_type, note, performed_by, created_at)
                VALUES (:orgId, :clientId, :amount, :balanceAfter, :type::balance_operation_type, :note, :performedBy, :createdAt)
                """.trimIndent(),
            )
            .bind("orgId", orgId).bind("clientId", clientId)
            .bind("amount", Money(amount, Currency.RUB))
            .bind("balanceAfter", Money(balanceAfter, Currency.RUB))
            .bind("type", operationType).bind("note", note)
            .bind("performedBy", performedBy).bind("createdAt", createdAt)
            .execute()
    }

    private suspend fun history(orgId: Uuid, clientId: ClientId): Either<DomainError, ClientBalanceHistoryResponse> {
        val context =
            EmployeeRequestContext(
                lang = Lang.RU,
                userId = UserId.new(),
                orgId = OrgId(orgId),
                branchId = BranchId.new(),
                employeeId = EmployeeId.new(),
                username = "test@example.com",
                clientIp = null,
                currency = Currency.RUB,
                permission = EmployeePermission(),
            )
        return either {
            val raise: Raise<DomainError> = this
            TestPostgres.db.transaction {
                context(context, this, raise) { view.byClient(clientId) }
            }
        }
    }

    private suspend fun succeeding(orgId: Uuid, clientId: ClientId): ClientBalanceHistoryResponse = assertIs<Either.Right<ClientBalanceHistoryResponse>>(history(orgId, clientId)).value

    @Test
    fun `клиент без операций отдаёт пустой журнал`() =
        runTest {
            val orgId = insertOrg()
            val clientId = insertClient(orgId, "Новичок")

            assertTrue(succeeding(orgId, clientId).entries.isEmpty())
        }

    @Test
    fun `возвращает операции от новых к старым с именем сотрудника`() =
        runTest {
            val orgId = insertOrg()
            val clientId = insertClient(orgId, "Клиент")
            val employeeId = insertEmployee(orgId, "Анна Админова")
            insertEntry(orgId, clientId, employeeId, 50_000, 50_000, baseInstant, note = "Пополнение")
            insertEntry(orgId, clientId, employeeId, -20_000, 30_000, baseInstant + 1.seconds, note = "Списание")

            val entries = succeeding(orgId, clientId).entries

            assertEquals(listOf("Списание", "Пополнение"), entries.map { it.note })
            assertEquals(Money(30_000, Currency.RUB), entries.first().balanceAfter)
            assertEquals(Money(-20_000, Currency.RUB), entries.first().amount)
            assertEquals("Анна Админова", entries.first().performedBy?.name)
            assertEquals(employeeId.value, entries.first().performedBy?.id)
        }

    @Test
    fun `сотрудник другой организации не подставляется`() =
        runTest {
            val orgId = insertOrg("Своя")
            val otherOrgId = insertOrg("Чужая")
            val clientId = insertClient(orgId, "Клиент")
            val foreignEmployee = insertEmployee(otherOrgId, "Чужой сотрудник")
            insertEntry(orgId, clientId, foreignEmployee, 10_000, 10_000, baseInstant)

            assertNull(succeeding(orgId, clientId).entries.single().performedBy)
        }

    @Test
    fun `журнал другого клиента не попадает в выборку`() =
        runTest {
            val orgId = insertOrg()
            val clientId = insertClient(orgId, "Наш")
            val otherClientId = insertClient(orgId, "Другой")
            val employeeId = insertEmployee(orgId, "Анна")
            insertEntry(orgId, clientId, employeeId, 10_000, 10_000, baseInstant, note = "Наша")
            insertEntry(orgId, otherClientId, employeeId, 20_000, 20_000, baseInstant, note = "Чужая")

            assertEquals(listOf("Наша"), succeeding(orgId, clientId).entries.map { it.note })
        }

    @Test
    fun `клиент чужой организации не находится`() =
        runTest {
            val orgA = insertOrg("A")
            val orgB = insertOrg("B")
            val foreignClient = insertClient(orgB, "Чужой")

            assertIs<Either.Left<DomainError>>(history(orgA, foreignClient))
        }
}
