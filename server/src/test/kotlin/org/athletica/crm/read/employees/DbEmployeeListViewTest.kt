package org.athletica.crm.read.employees

import arrow.core.getOrElse
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.employeeContext
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.uuid.Uuid

/** Тесты read-проекции списка сотрудников: скоуп организации, сортировка и названия ролей. */
class DbEmployeeListViewTest {
    private val orgId = OrgId.new()
    private val otherOrgId = OrgId.new()

    private val view = DbEmployeeListView()

    private val ctx =
        employeeContext(
            lang = Lang.RU,
            orgId = orgId,
            currency = Currency.RUB,
            userId = UserId.new(),
            branchId = BranchId.new(),
            employeeId = EmployeeId.new(),
            username = "test@example.com",
            clientIp = null,
            permissions = EmployeePermission(),
        )

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .bind("id", orgId).bind("name", "Org").execute()
            TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .bind("id", otherOrgId).bind("name", "Other Org").execute()
        }
    }

    /** Вставляет сотрудника [name] в организацию [org]; [isOwner] — признак владельца. */
    private suspend fun insertEmployee(name: String, org: OrgId = orgId, isOwner: Boolean = false): EmployeeId {
        val id = EmployeeId.new()
        TestPostgres.db
            .sql("INSERT INTO employees (id, org_id, name, is_owner) VALUES (:id, :orgId, :name, :isOwner)")
            .bind("id", id).bind("orgId", org).bind("name", name).bind("isOwner", isOwner)
            .execute()
        return id
    }

    /** Создаёт роль [name] и назначает её сотруднику [employeeId]. */
    private suspend fun assignRole(employeeId: EmployeeId, name: String): Uuid {
        val roleId = Uuid.random()
        TestPostgres.db
            .sql("INSERT INTO roles (id, org_id, name) VALUES (:id, :orgId, :name)")
            .bind("id", roleId).bind("orgId", orgId).bind("name", name)
            .execute()
        TestPostgres.db
            .sql("INSERT INTO employee_roles (employee_id, role_id) VALUES (:employeeId, :roleId)")
            .bind("employeeId", employeeId).bind("roleId", roleId)
            .execute()
        return roleId
    }

    private suspend fun list() =
        either {
            TestPostgres.db.transaction { context(ctx) { view.list() } }
        }.getOrElse { fail("Unexpected error: $it") }

    @Test
    fun `возвращает пустой список если сотрудников нет`() =
        runTest {
            val response = list()

            assertTrue(response.employees.isEmpty())
            assertEquals(0u, response.total)
        }

    @Test
    fun `возвращает только сотрудников своей организации`() =
        runTest {
            insertEmployee("Свой")
            insertEmployee("Чужой", org = otherOrgId)

            val response = list()

            assertEquals(listOf("Свой"), response.employees.map { it.name })
            assertEquals(1u, response.total)
        }

    @Test
    fun `возвращает владельца первым, остальных по имени`() =
        runTest {
            insertEmployee("Яков")
            insertEmployee("Антон")
            insertEmployee("Михаил")
            insertEmployee("Борис", isOwner = true)

            val response = list()

            assertEquals(listOf("Борис", "Антон", "Михаил", "Яков"), response.employees.map { it.name })
            assertTrue(response.employees.first().isOwner)
        }

    @Test
    fun `включает названия ролей каждого сотрудника`() =
        runTest {
            val withRole = insertEmployee("Сотрудник")
            insertEmployee("Без ролей")
            val roleId = assignRole(withRole, "Администратор")

            val employees = list().employees.associateBy { it.name }

            assertEquals(1, employees.getValue("Сотрудник").roles.size)
            assertEquals(roleId, employees.getValue("Сотрудник").roles.first().id)
            assertEquals("Администратор", employees.getValue("Сотрудник").roles.first().name)
            assertTrue(employees.getValue("Без ролей").roles.isEmpty())
        }
}
