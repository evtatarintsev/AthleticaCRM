package org.athletica.crm.domain

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.Lang
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.toEmailAddress
import org.athletica.crm.domain.auth.DbUsers
import org.athletica.crm.domain.employees.DbEmployee
import org.athletica.crm.domain.employees.DbEmployees
import org.athletica.crm.domain.employees.DbRoles
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.domain.employees.EmployeeRole
import org.athletica.crm.security.PasswordHasher
import org.junit.Before
import kotlin.collections.emptyList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.uuid.Uuid

class DbEmployeesTest {
    private val orgId = OrgId.new()
    private val otherOrgId = OrgId.new()

    private val ctx = RequestContext(Lang.EN, UserId.new(), orgId, "owner@example.com", "127.0.0.1")
    private val otherCtx = RequestContext(Lang.EN, UserId.new(), otherOrgId, "other@example.com", "127.0.0.1")
    private val users = DbUsers(PasswordHasher())
    private val roles = DbRoles()
    private val employees = DbEmployees(users, roles)

    private val emptyPermission = EmployeePermission(emptyList(), emptySet(), emptySet())

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .bind("id", orgId).bind("name", "Org 1").execute()
            TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .bind("id", otherOrgId).bind("name", "Org 2").execute()
        }
    }

    // ─── new ──────────────────────────────────────────────────────────────────

    @Test
    fun `new создаёт сотрудника с корректными полями`() =
        runTest {
            val id = EmployeeId.new()
            val employee =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) {
                            employees.new(id, "Иван Иванов", "+71234567890", "ivan@example.com".toEmailAddress(), null, emptyPermission)
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(id, employee.id)
            assertEquals("Иван Иванов", employee.name)
            assertEquals("+71234567890", employee.phoneNo)
            assertEquals("ivan@example.com", employee.email?.value)
            assertNull(employee.avatarId)
            assertEquals(false, employee.isActive)
            assertEquals(false, employee.isOwner)
            assertNull(employee.userId)
            assertTrue(employee.permissions.roles.isEmpty())
        }

    @Test
    fun `new создаёт сотрудника с минимальными полями`() =
        runTest {
            val id = EmployeeId.new()
            val employee =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) {
                            employees.new(id, "Анна", null, null, null, emptyPermission)
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(id, employee.id)
            assertEquals("Анна", employee.name)
            assertNull(employee.phoneNo)
            assertNull(employee.email)
        }

    @Test
    fun `new возвращает EMPLOYEE_ALREADY_EXISTS при дублировании id`() =
        runTest {
            val id = EmployeeId.new()
            either<DomainError, _> {
                TestPostgres.db.transaction {
                    context(ctx, this) { employees.new(id, "Первый", null, null, null, emptyPermission) }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { employees.new(id, "Второй", null, null, null, emptyPermission) }
                    }
                }
            assertEquals("EMPLOYEE_ALREADY_EXISTS", assertIs<Either.Left<DomainError>>(result).value.code)
        }

    // ─── byId ─────────────────────────────────────────────────────────────────

    @Test
    fun `byId возвращает сотрудника по id`() =
        runTest {
            val id = EmployeeId.new()
            either<DomainError, _> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        employees.new(id, "Пётр Петров", "89001234567", "petr@example.com".toEmailAddress(), null, emptyPermission)
                    }
                }
                val employee = TestPostgres.db.transaction { context(ctx, this) { employees.byId(id) } }
                assertEquals(id, employee.id)
                assertEquals("Пётр Петров", employee.name)
                assertEquals("89001234567", employee.phoneNo)
                assertEquals("petr@example.com", employee.email?.value)
                assertEquals(false, employee.isActive)
                assertEquals(false, employee.isOwner)
                assertNull(employee.userId)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `byId возвращает EMPLOYEE_NOT_FOUND для неизвестного id`() =
        runTest {
            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction { context(ctx, this) { employees.byId(EmployeeId.new()) } }
                }
            assertEquals("EMPLOYEE_NOT_FOUND", assertIs<Either.Left<DomainError>>(result).value.code)
        }

    @Test
    fun `byId возвращает EMPLOYEE_NOT_FOUND для id из другой организации`() =
        runTest {
            val id = EmployeeId.new()
            either<DomainError, _> {
                TestPostgres.db.transaction {
                    context(ctx, this) { employees.new(id, "Сотрудник", null, null, null, emptyPermission) }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction { context(otherCtx, this) { employees.byId(id) } }
                }
            assertEquals("EMPLOYEE_NOT_FOUND", assertIs<Either.Left<DomainError>>(result).value.code)
        }

    @Test
    fun `byId загружает роли сотрудника`() =
        runTest {
            val id = EmployeeId.new()
            val roleId = Uuid.random()

            either<DomainError, _> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        employees.new(id, "Менеджер", null, null, null, emptyPermission)
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }

            runBlocking {
                TestPostgres.db
                    .sql("INSERT INTO roles (id, org_id, name) VALUES (:id, :orgId, :name)")
                    .bind("id", roleId)
                    .bind("orgId", orgId)
                    .bind("name", "Тренер")
                    .execute()
                TestPostgres.db
                    .sql("INSERT INTO employee_roles (employee_id, role_id) VALUES (:employeeId, :roleId)")
                    .bind("employeeId", id)
                    .bind("roleId", roleId)
                    .execute()
            }

            val employee =
                either<DomainError, _> {
                    TestPostgres.db.transaction { context(ctx, this) { employees.byId(id) } }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(1, employee.permissions.roles.size)
            assertEquals(roleId, employee.permissions.roles.first().id)
            assertEquals("Тренер", employee.permissions.roles.first().name)
        }

    @Test
    fun `byId возвращает пустой список ролей если ролей нет`() =
        runTest {
            val id = EmployeeId.new()
            either<DomainError, _> {
                TestPostgres.db.transaction {
                    context(ctx, this) { employees.new(id, "Без ролей", null, null, null, emptyPermission) }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val employee =
                either<DomainError, _> {
                    TestPostgres.db.transaction { context(ctx, this) { employees.byId(id) } }
                }.getOrElse { fail("Unexpected error: $it") }

            assertTrue(employee.permissions.roles.isEmpty())
        }

    // ─── list ─────────────────────────────────────────────────────────────────

    @Test
    fun `list возвращает пустой список если сотрудников нет`() =
        runTest {
            either<DomainError, _> {
                val list = TestPostgres.db.transaction { context(ctx, this) { employees.list() } }
                assertTrue(list.isEmpty())
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `list возвращает только сотрудников своей организации`() =
        runTest {
            either<DomainError, _> {
                TestPostgres.db.transaction {
                    context(ctx, this) { employees.new(EmployeeId.new(), "Свой", null, null, null, emptyPermission) }
                }
                TestPostgres.db.transaction {
                    context(otherCtx, this) { employees.new(EmployeeId.new(), "Чужой", null, null, null, emptyPermission) }
                }
                val list = TestPostgres.db.transaction { context(ctx, this) { employees.list() } }
                assertEquals(1, list.size)
                assertEquals("Свой", list.first().name)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `list возвращает сотрудников отсортированных по имени`() =
        runTest {
            either<DomainError, _> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        employees.new(EmployeeId.new(), "Яков", null, null, null, emptyPermission)
                        employees.new(EmployeeId.new(), "Антон", null, null, null, emptyPermission)
                        employees.new(EmployeeId.new(), "Михаил", null, null, null, emptyPermission)
                    }
                }
                val list = TestPostgres.db.transaction { context(ctx, this) { employees.list() } }
                assertEquals(listOf("Антон", "Михаил", "Яков"), list.map { it.name })
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `list включает роли для каждого сотрудника`() =
        runTest {
            val id = EmployeeId.new()
            val roleId = Uuid.random()

            either<DomainError, _> {
                TestPostgres.db.transaction {
                    context(ctx, this) { employees.new(id, "Сотрудник", null, null, null, emptyPermission) }
                }
            }.getOrElse { fail("Setup failed: $it") }

            runBlocking {
                TestPostgres.db
                    .sql("INSERT INTO roles (id, org_id, name) VALUES (:id, :orgId, :name)")
                    .bind("id", roleId)
                    .bind("orgId", orgId)
                    .bind("name", "Администратор")
                    .execute()
                TestPostgres.db
                    .sql("INSERT INTO employee_roles (employee_id, role_id) VALUES (:employeeId, :roleId)")
                    .bind("employeeId", id)
                    .bind("roleId", roleId)
                    .execute()
            }

            either<DomainError, _> {
                val list = TestPostgres.db.transaction { context(ctx, this) { employees.list() } }
                val employee = list.first()
                assertEquals(1, employee.permissions.roles.size)
                assertEquals("Администратор", employee.permissions.roles.first().name)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    // ─── save ─────────────────────────────────────────────────────────────────

    @Test
    fun `save обновляет поля сотрудника в БД`() =
        runTest {
            val id = EmployeeId.new()
            either {
                val employee =
                    TestPostgres.db.transaction {
                        context(ctx, this) { employees.new(id, "Старое имя", null, null, null, emptyPermission) }
                    }

                TestPostgres.db.transaction {
                    context(this, ctx) {
                        (employee as DbEmployee)
                            .copy(name = "Новое имя", phoneNo = "+79001112233", isActive = true)
                            .save()
                    }
                }

                val updated = TestPostgres.db.transaction { context(ctx, this) { employees.byId(id) } }
                assertEquals("Новое имя", updated.name)
                assertEquals("+79001112233", updated.phoneNo)
                assertEquals(true, updated.isActive)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `save обновляет email сотрудника`() =
        runTest {
            val id = EmployeeId.new()
            either<DomainError, _> {
                val employee =
                    TestPostgres.db.transaction {
                        context(ctx, this) { employees.new(id, "Сотрудник", null, null, null, emptyPermission) }
                    }

                TestPostgres.db.transaction {
                    context(this, ctx) {
                        (employee as DbEmployee)
                            .copy(email = EmailAddress("newemail@example.com"))
                            .save()
                    }
                }

                val updated = TestPostgres.db.transaction { context(ctx, this) { employees.byId(id) } }
                assertEquals("newemail@example.com", updated.email?.value)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `save сохраняет роли сотрудника в БД`() =
        runTest {
            val id = EmployeeId.new()
            either<DomainError, _> {
                val createdRoles =
                    TestPostgres.db.transaction {
                        context(ctx, this) {
                            roles.new(EmployeeRole(Uuid.random(), "Тренер", emptySet()))
                            roles.new(EmployeeRole(Uuid.random(), "Менеджер", emptySet()))
                            roles.list()
                        }
                    }

                val roleIds = createdRoles.map { it.id }
                val permission = EmployeePermission(roles = createdRoles, grantedPermissions = emptySet(), revokedPermissions = emptySet())
                val employee =
                    TestPostgres.db.transaction {
                        context(ctx, this) { employees.new(id, "Сотрудник с ролями", null, null, null, permission) }
                    }

                // Verify roles were saved on creation
                var retrieved = TestPostgres.db.transaction { context(ctx, this) { employees.byId(id) } }
                assertEquals(2, retrieved.permissions.roles.size)
                assertTrue(retrieved.permissions.roles.map { it.id }.containsAll(roleIds))

                // Modify permissions and save
                val updatedPermission = EmployeePermission(roles = createdRoles.drop(1), grantedPermissions = emptySet(), revokedPermissions = emptySet())
                TestPostgres.db.transaction {
                    context(this, ctx) {
                        (employee as DbEmployee)
                            .copy(permissions = updatedPermission)
                            .save()
                    }
                }

                // Verify role changes were persisted
                retrieved = TestPostgres.db.transaction { context(ctx, this) { employees.byId(id) } }
                assertEquals(1, retrieved.permissions.roles.size)
                assertEquals(createdRoles[1].id, retrieved.permissions.roles.first().id)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `save удаляет роли и вставляет новые при обновлении`() =
        runTest {
            val id = EmployeeId.new()
            either<DomainError, _> {
                val createdRoles =
                    TestPostgres.db.transaction {
                        context(ctx, this) {
                            roles.new(EmployeeRole(Uuid.random(), "Роль 1", emptySet()))
                            roles.new(EmployeeRole(Uuid.random(), "Роль 2", emptySet()))
                            roles.new(EmployeeRole(Uuid.random(), "Роль 3", emptySet()))
                            roles.list()
                        }
                    }

                val initialPermission = EmployeePermission(roles = createdRoles.take(2), grantedPermissions = emptySet(), revokedPermissions = emptySet())
                val employee =
                    TestPostgres.db.transaction {
                        context(ctx, this) { employees.new(id, "Сотрудник", null, null, null, initialPermission) }
                    }

                // Replace roles: remove first two, add the third
                val newPermission = EmployeePermission(roles = listOf(createdRoles[2]), grantedPermissions = emptySet(), revokedPermissions = emptySet())
                TestPostgres.db.transaction {
                    context(this, ctx) {
                        (employee as DbEmployee)
                            .copy(permissions = newPermission)
                            .save()
                    }
                }

                val updated = TestPostgres.db.transaction { context(ctx, this) { employees.byId(id) } }
                assertEquals(1, updated.permissions.roles.size)
                assertEquals(createdRoles[2].id, updated.permissions.roles.first().id)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    // ─── invite ───────────────────────────────────────────────────────────────

    @Test
    fun `invite создаёт пользователя и активирует сотрудника`() =
        runTest {
            val id = EmployeeId.new()
            either<DomainError, _> {
                val employee =
                    TestPostgres.db.transaction {
                        context(ctx, this) { employees.new(id, "Новый сотрудник", null, null, null, emptyPermission) }
                    }

                assertNull(employee.userId)
                assertEquals(false, employee.isActive)

                TestPostgres.db.transaction {
                    context(ctx, this) {
                        employee.invite("invite@example.com".toEmailAddress(), "secret123")
                    }
                }

                val updated = TestPostgres.db.transaction { context(ctx, this) { employees.byId(id) } }
                assertNotNull(updated.userId)
                assertEquals(true, updated.isActive)
                assertEquals("invite@example.com", updated.email?.value)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `invite возвращает LOGIN_TAKEN если email уже занят`() =
        runTest {
            val id1 = EmployeeId.new()
            val id2 = EmployeeId.new()
            val email = "taken@example.com".toEmailAddress()

            either<DomainError, _> {
                val emp1 =
                    TestPostgres.db.transaction {
                        context(ctx, this) { employees.new(id1, "Первый", null, null, null, emptyPermission) }
                    }
                TestPostgres.db.transaction {
                    context(ctx, this) { emp1.invite(email, "pass1") }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val emp2 =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { employees.new(id2, "Второй", null, null, null, emptyPermission) }
                    }
                }.getOrElse { fail("Setup failed: $it") }

            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { emp2.invite(email, "pass2") }
                    }
                }
            assertEquals("LOGIN_TAKEN", assertIs<Either.Left<DomainError>>(result).value.code)
        }
}
