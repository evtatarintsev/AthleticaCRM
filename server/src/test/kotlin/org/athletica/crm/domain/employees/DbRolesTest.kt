package org.athletica.crm.domain.employees

import arrow.core.getOrElse
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.Lang
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.permissions.Permission
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.uuid.Uuid

class DbRolesTest {
    private val orgId = OrgId.new()
    private val otherOrgId = OrgId.new()
    private val employeeId = EmployeeId.new()
    private val otherEmployeeId = EmployeeId.new()

    private val ctx = RequestContext(Lang.EN, UserId.new(), orgId, employeeId, "user@example.com", "127.0.0.1", EmployeePermission())
    private val otherCtx = RequestContext(Lang.EN, UserId.new(), otherOrgId, otherEmployeeId, "user@example.com", "127.0.0.1", EmployeePermission())

    private lateinit var roles: DbRoles

    @Before
    fun setUp() {
        TestPostgres.truncate()
        roles = DbRoles()
        runBlocking {
            TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .bind("id", orgId).bind("name", "Org 1").execute()
            TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .bind("id", otherOrgId).bind("name", "Org 2").execute()
        }
    }

    // ─── list ─────────────────────────────────────────────────────────────────

    @Test
    fun `list возвращает пустой список если ролей нет`() =
        runTest {
            either<DomainError, Unit> {
                val list = TestPostgres.db.transaction { context(ctx, this) { roles.list() } }
                assertTrue(list.isEmpty())
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `list возвращает только роли своей организации`() =
        runTest {
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) { roles.new(EmployeeRole(Uuid.random(), "Тренер", emptySet())) }
                }
                TestPostgres.db.transaction {
                    context(otherCtx, this) { roles.new(EmployeeRole(Uuid.random(), "Менеджер", emptySet())) }
                }
                val list = TestPostgres.db.transaction { context(ctx, this) { roles.list() } }
                assertEquals(1, list.size)
                assertEquals("Тренер", list.first().name)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `list возвращает несколько ролей`() =
        runTest {
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        roles.new(EmployeeRole(Uuid.random(), "Тренер", emptySet()))
                        roles.new(EmployeeRole(Uuid.random(), "Менеджер", emptySet()))
                        roles.new(EmployeeRole(Uuid.random(), "Администратор", emptySet()))
                    }
                }
                val list = TestPostgres.db.transaction { context(ctx, this) { roles.list() } }
                assertEquals(3, list.size)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    // ─── new ──────────────────────────────────────────────────────────────────

    @Test
    fun `new создаёт роль без прав`() =
        runTest {
            val id = Uuid.random()
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) { roles.new(EmployeeRole(id, "Тренер", emptySet())) }
                }
                val list = TestPostgres.db.transaction { context(ctx, this) { roles.list() } }
                assertEquals(1, list.size)
                assertEquals(id, list.first().id)
                assertEquals("Тренер", list.first().name)
                assertTrue(list.first().permissions.isEmpty())
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `new создаёт роль с правами`() =
        runTest {
            val id = Uuid.random()
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        roles.new(EmployeeRole(id, "Менеджер", setOf(Permission.CAN_VIEW_CLIENT_BALANCE)))
                    }
                }
                val list = TestPostgres.db.transaction { context(ctx, this) { roles.list() } }
                assertEquals(1, list.size)
                assertEquals(setOf(Permission.CAN_VIEW_CLIENT_BALANCE), list.first().permissions)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `new создаёт роль с несколькими правами`() =
        runTest {
            val id = Uuid.random()
            val permissions = Permission.entries.toSet()
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) { roles.new(EmployeeRole(id, "Суперменеджер", permissions)) }
                }
                val list = TestPostgres.db.transaction { context(ctx, this) { roles.list() } }
                assertEquals(permissions, list.first().permissions)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `new не смешивает права разных ролей`() =
        runTest {
            val id1 = Uuid.random()
            val id2 = Uuid.random()
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        roles.new(EmployeeRole(id1, "Роль с правами", setOf(Permission.CAN_VIEW_CLIENT_BALANCE)))
                        roles.new(EmployeeRole(id2, "Роль без прав", emptySet()))
                    }
                }
                val list = TestPostgres.db.transaction { context(ctx, this) { roles.list() } }
                val role1 = list.first { it.id == id1 }
                val role2 = list.first { it.id == id2 }
                assertEquals(setOf(Permission.CAN_VIEW_CLIENT_BALANCE), role1.permissions)
                assertTrue(role2.permissions.isEmpty())
            }.getOrElse { fail("Unexpected error: $it") }
        }
}
