package org.athletica.crm.read.employees

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.employees.EmployeeDetailResponse
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.employeeContext
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.uuid.Uuid

/** Тесты read-проекции карточки сотрудника: поля, роли, выдачи и отзывы прав, филиалы. */
class DbEmployeeDetailViewTest {
    private val orgId = OrgId.new()
    private val otherOrgId = OrgId.new()

    private val view = DbEmployeeDetailView()

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

    /** Вставляет сотрудника [name] в организацию [org]; [allBranchesAccess] — доступ ко всем филиалам. */
    private suspend fun insertEmployee(name: String, org: OrgId = orgId, allBranchesAccess: Boolean = true): EmployeeId {
        val id = EmployeeId.new()
        TestPostgres.db
            .sql(
                """
                INSERT INTO employees (id, org_id, name, phone_no, email, all_branches_access)
                VALUES (:id, :orgId, :name, '+79001112233', 'emp@example.com', :all)
                """.trimIndent(),
            )
            .bind("id", id).bind("orgId", org).bind("name", name).bind("all", allBranchesAccess)
            .execute()
        return id
    }

    private suspend fun detail(id: EmployeeId): Either<DomainError, EmployeeDetailResponse> =
        either {
            TestPostgres.db.transaction { context(ctx) { view.byId(id) } }
        }

    @Test
    fun `возвращает поля сотрудника`() =
        runTest {
            val id = insertEmployee("Иван")

            val response = detail(id).getOrElse { fail("Unexpected error: $it") }

            assertEquals(id, response.id)
            assertEquals("Иван", response.name)
            assertEquals("+79001112233", response.phoneNo)
            assertEquals("emp@example.com", response.email)
            assertNull(response.avatarId)
            assertEquals(false, response.isOwner)
            assertTrue(response.roles.isEmpty())
            assertTrue(response.grantedPermissions.isEmpty())
            assertTrue(response.revokedPermissions.isEmpty())
            assertTrue(response.allBranchesAccess)
            assertTrue(response.branchIds.isEmpty())
        }

    @Test
    fun `возвращает роли, персональные выдачи и отзывы прав`() =
        runTest {
            val id = insertEmployee("Менеджер")
            val roleId = Uuid.random()
            TestPostgres.db
                .sql("INSERT INTO roles (id, org_id, name) VALUES (:id, :orgId, 'Тренер')")
                .bind("id", roleId).bind("orgId", orgId)
                .execute()
            TestPostgres.db
                .sql("INSERT INTO employee_roles (employee_id, role_id) VALUES (:employeeId, :roleId)")
                .bind("employeeId", id).bind("roleId", roleId)
                .execute()
            TestPostgres.db
                .sql(
                    """
                    INSERT INTO employee_permission_overrides (employee_id, permission_key, is_granted)
                    VALUES (:id, :granted, true), (:id, :revoked, false)
                    """.trimIndent(),
                )
                .bind("id", id)
                .bind("granted", UserPermission.CAN_VIEW_ALL_TASKS.name)
                .bind("revoked", UserPermission.CAN_VIEW_CLIENT_BALANCE.name)
                .execute()

            val response = detail(id).getOrElse { fail("Unexpected error: $it") }

            assertEquals(listOf(roleId), response.roles.map { it.id })
            assertEquals("Тренер", response.roles.first().name)
            assertEquals(setOf(UserPermission.CAN_VIEW_ALL_TASKS), response.grantedPermissions)
            assertEquals(setOf(UserPermission.CAN_VIEW_CLIENT_BALANCE), response.revokedPermissions)
        }

    @Test
    fun `возвращает выбранные филиалы при ограниченном доступе`() =
        runTest {
            val id = insertEmployee("Тренер", allBranchesAccess = false)
            val branchId = BranchId.new()
            TestPostgres.db
                .sql("INSERT INTO branches (id, org_id, name) VALUES (:id, :orgId, 'Центр')")
                .bind("id", branchId).bind("orgId", orgId)
                .execute()
            TestPostgres.db
                .sql("INSERT INTO employee_branches (employee_id, branch_id) VALUES (:employeeId, :branchId)")
                .bind("employeeId", id).bind("branchId", branchId)
                .execute()

            val response = detail(id).getOrElse { fail("Unexpected error: $it") }

            assertEquals(false, response.allBranchesAccess)
            assertEquals(listOf(branchId), response.branchIds)
        }

    @Test
    fun `возвращает EMPLOYEE_NOT_FOUND для неизвестного id`() =
        runTest {
            val result = detail(EmployeeId.new())

            assertEquals("EMPLOYEE_NOT_FOUND", assertIs<Either.Left<DomainError>>(result).value.code)
        }

    @Test
    fun `возвращает EMPLOYEE_NOT_FOUND для сотрудника другой организации`() =
        runTest {
            val id = insertEmployee("Чужой", org = otherOrgId)

            val result = detail(id)

            assertEquals("EMPLOYEE_NOT_FOUND", assertIs<Either.Left<DomainError>>(result).value.code)
        }
}
