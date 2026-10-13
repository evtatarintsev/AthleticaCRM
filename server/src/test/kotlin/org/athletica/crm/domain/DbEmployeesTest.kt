package org.athletica.crm.domain

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.core.toEmailAddress
import org.athletica.crm.domain.auth.DbUsers
import org.athletica.crm.domain.employees.DbEmployee
import org.athletica.crm.domain.employees.DbEmployees
import org.athletica.crm.domain.employees.DbRoles
import org.athletica.crm.domain.employees.Employee
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.domain.employees.EmployeeRole
import org.athletica.crm.employeeContext
import org.athletica.crm.security.PasswordHasher
import org.junit.Before
import kotlin.collections.emptyList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.uuid.Uuid

class DbEmployeesTest {
    private val orgId = OrgId.new()
    private val otherOrgId = OrgId.new()
    private val employeeId = EmployeeId.new()
    private val otherEmployeeId = EmployeeId.new()

    private val ctx = employeeContext(lang = Lang.EN, orgId = orgId, currency = Currency.RUB, userId = UserId.new(), branchId = BranchId.new(), employeeId = employeeId, username = "owner@example.com", clientIp = "127.0.0.1", permissions = EmployeePermission())
    private val otherCtx = employeeContext(lang = Lang.EN, orgId = otherOrgId, currency = Currency.RUB, userId = UserId.new(), branchId = BranchId.new(), employeeId = otherEmployeeId, username = "other@example.com", clientIp = "127.0.0.1", permissions = EmployeePermission())
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
                either {
                    TestPostgres.db.transaction {
                        context(ctx) {
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
            assertTrue(UserPermission.entries.none { employee.hasPermission(it) })
        }

    @Test
    fun `new создаёт сотрудника с минимальными полями`() =
        runTest {
            val id = EmployeeId.new()
            val employee =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) {
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
            either {
                TestPostgres.db.transaction {
                    context(ctx) { employees.new(id, "Первый", null, null, null, emptyPermission) }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val result =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) { employees.new(id, "Второй", null, null, null, emptyPermission) }
                    }
                }
            assertEquals("EMPLOYEE_ALREADY_EXISTS", assertIs<Either.Left<DomainError>>(result).value.code)
        }

    // ─── byId ─────────────────────────────────────────────────────────────────

    @Test
    fun `byId возвращает сотрудника по id`() =
        runTest {
            val id = EmployeeId.new()
            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        employees.new(id, "Пётр Петров", "89001234567", "petr@example.com".toEmailAddress(), null, emptyPermission)
                    }
                }
                val employee = TestPostgres.db.transaction { context(ctx) { employees.byId(id) } }
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
                either {
                    TestPostgres.db.transaction { context(ctx) { employees.byId(EmployeeId.new()) } }
                }
            assertEquals("EMPLOYEE_NOT_FOUND", assertIs<Either.Left<DomainError>>(result).value.code)
        }

    @Test
    fun `byId возвращает EMPLOYEE_NOT_FOUND для id из другой организации`() =
        runTest {
            val id = EmployeeId.new()
            either {
                TestPostgres.db.transaction {
                    context(ctx) { employees.new(id, "Сотрудник", null, null, null, emptyPermission) }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val result =
                either {
                    TestPostgres.db.transaction { context(otherCtx) { employees.byId(id) } }
                }
            assertEquals("EMPLOYEE_NOT_FOUND", assertIs<Either.Left<DomainError>>(result).value.code)
        }

    @Test
    fun `byId загружает права ролей сотрудника`() =
        runTest {
            val id = EmployeeId.new()
            val roleId = Uuid.random()

            either {
                TestPostgres.db.transaction {
                    context(ctx) {
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
                    .sql("INSERT INTO role_permissions (role_id, permission_key) VALUES (:roleId, :key)")
                    .bind("roleId", roleId)
                    .bind("key", UserPermission.CAN_VIEW_CLIENT_BALANCE.name)
                    .execute()
                TestPostgres.db
                    .sql("INSERT INTO employee_roles (employee_id, role_id) VALUES (:employeeId, :roleId)")
                    .bind("employeeId", id)
                    .bind("roleId", roleId)
                    .execute()
            }

            val employee =
                either {
                    TestPostgres.db.transaction { context(ctx) { employees.byId(id) } }
                }.getOrElse { fail("Unexpected error: $it") }

            assertTrue(employee.hasPermission(UserPermission.CAN_VIEW_CLIENT_BALANCE))
            assertFalse(employee.hasPermission(UserPermission.CAN_MANAGE_TASKS))
        }

    @Test
    fun `byId - сотрудник без ролей и выдач не имеет прав`() =
        runTest {
            val id = EmployeeId.new()
            either {
                TestPostgres.db.transaction {
                    context(ctx) { employees.new(id, "Без ролей", null, null, null, emptyPermission) }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val employee =
                either {
                    TestPostgres.db.transaction { context(ctx) { employees.byId(id) } }
                }.getOrElse { fail("Unexpected error: $it") }

            assertTrue(UserPermission.entries.none { employee.hasPermission(it) })
        }

    @Test
    fun `byId - персональный отзыв перекрывает право роли`() =
        runTest {
            val id = EmployeeId.new()
            either {
                val role = EmployeeRole(Uuid.random(), "Кассир", setOf(UserPermission.CAN_VIEW_CLIENT_BALANCE, UserPermission.CAN_MANAGE_ORG_BALANCE))
                TestPostgres.db.transaction {
                    context(ctx) {
                        roles.new(role)
                        employees.new(
                            id,
                            "Кассир",
                            null,
                            null,
                            null,
                            EmployeePermission(
                                roles = listOf(role),
                                grantedPermissions = emptySet(),
                                revokedPermissions = setOf(UserPermission.CAN_MANAGE_ORG_BALANCE),
                            ),
                        )
                    }
                }

                val employee = TestPostgres.db.transaction { context(ctx) { employees.byId(id) } }
                assertTrue(employee.hasPermission(UserPermission.CAN_VIEW_CLIENT_BALANCE))
                assertFalse(employee.hasPermission(UserPermission.CAN_MANAGE_ORG_BALANCE))
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
                        context(ctx) { employees.new(id, "Старое имя", null, null, null, emptyPermission) }
                    }

                TestPostgres.db.transaction {
                    context(this, ctx) {
                        (employee as DbEmployee)
                            .copy(name = "Новое имя", phoneNo = "+79001112233", isActive = true)
                            .save()
                    }
                }

                val updated = TestPostgres.db.transaction { context(ctx) { employees.byId(id) } }
                assertEquals("Новое имя", updated.name)
                assertEquals("+79001112233", updated.phoneNo)
                assertEquals(true, updated.isActive)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `save обновляет email сотрудника`() =
        runTest {
            val id = EmployeeId.new()
            either {
                val employee =
                    TestPostgres.db.transaction {
                        context(ctx) { employees.new(id, "Сотрудник", null, null, null, emptyPermission) }
                    }

                TestPostgres.db.transaction {
                    context(this, ctx) {
                        (employee as DbEmployee)
                            .copy(email = EmailAddress("newemail@example.com"))
                            .save()
                    }
                }

                val updated = TestPostgres.db.transaction { context(ctx) { employees.byId(id) } }
                assertEquals("newemail@example.com", updated.email?.value)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `save сохраняет роли сотрудника в БД`() =
        runTest {
            val id = EmployeeId.new()
            either {
                val trainer = EmployeeRole(Uuid.random(), "Тренер", setOf(UserPermission.CAN_VIEW_ALL_TASKS))
                val manager = EmployeeRole(Uuid.random(), "Менеджер", setOf(UserPermission.CAN_MANAGE_TASKS))
                TestPostgres.db.transaction {
                    context(ctx) {
                        roles.new(trainer)
                        roles.new(manager)
                    }
                }

                val permission = EmployeePermission(roles = listOf(trainer, manager), grantedPermissions = emptySet(), revokedPermissions = emptySet())
                val employee =
                    TestPostgres.db.transaction {
                        context(ctx) { employees.new(id, "Сотрудник с ролями", null, null, null, permission) }
                    }

                var retrieved = TestPostgres.db.transaction { context(ctx) { employees.byId(id) } }
                assertTrue(retrieved.hasPermission(UserPermission.CAN_VIEW_ALL_TASKS))
                assertTrue(retrieved.hasPermission(UserPermission.CAN_MANAGE_TASKS))

                val updatedPermission = EmployeePermission(roles = listOf(manager), grantedPermissions = emptySet(), revokedPermissions = emptySet())
                TestPostgres.db.transaction {
                    context(this, ctx) {
                        employee.withNewPermissions(updatedPermission).save()
                    }
                }

                retrieved = TestPostgres.db.transaction { context(ctx) { employees.byId(id) } }
                assertFalse(retrieved.hasPermission(UserPermission.CAN_VIEW_ALL_TASKS))
                assertTrue(retrieved.hasPermission(UserPermission.CAN_MANAGE_TASKS))
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `save заменяет роли и персональные права при обновлении`() =
        runTest {
            val id = EmployeeId.new()
            either {
                val first = EmployeeRole(Uuid.random(), "Роль 1", setOf(UserPermission.CAN_VIEW_ALL_TASKS))
                val second = EmployeeRole(Uuid.random(), "Роль 2", setOf(UserPermission.CAN_MANAGE_TASKS))
                val third = EmployeeRole(Uuid.random(), "Роль 3", setOf(UserPermission.CAN_VIEW_CLIENT_BALANCE))
                TestPostgres.db.transaction {
                    context(ctx) {
                        roles.new(first)
                        roles.new(second)
                        roles.new(third)
                    }
                }

                val initialPermission = EmployeePermission(roles = listOf(first, second), grantedPermissions = emptySet(), revokedPermissions = emptySet())
                val employee =
                    TestPostgres.db.transaction {
                        context(ctx) { employees.new(id, "Сотрудник", null, null, null, initialPermission) }
                    }

                val newPermission =
                    EmployeePermission(
                        roles = listOf(third),
                        grantedPermissions = setOf(UserPermission.CAN_MANAGE_ORG_BALANCE),
                        revokedPermissions = emptySet(),
                    )
                TestPostgres.db.transaction {
                    context(this, ctx) {
                        employee.withNewPermissions(newPermission).save()
                    }
                }

                val updated = TestPostgres.db.transaction { context(ctx) { employees.byId(id) } }
                assertFalse(updated.hasPermission(UserPermission.CAN_VIEW_ALL_TASKS))
                assertFalse(updated.hasPermission(UserPermission.CAN_MANAGE_TASKS))
                assertTrue(updated.hasPermission(UserPermission.CAN_VIEW_CLIENT_BALANCE))
                assertTrue(updated.hasPermission(UserPermission.CAN_MANAGE_ORG_BALANCE))
            }.getOrElse { fail("Unexpected error: $it") }
        }

    // ─── invite ───────────────────────────────────────────────────────────────

    @Test
    fun `invite создаёт пользователя и активирует сотрудника`() =
        runTest {
            val id = EmployeeId.new()
            either {
                val employee =
                    TestPostgres.db.transaction {
                        context(ctx) { employees.new(id, "Новый сотрудник", null, null, null, emptyPermission) }
                    }

                assertNull(employee.userId)
                assertEquals(false, employee.isActive)

                TestPostgres.db.transaction {
                    context(ctx) {
                        employee.invite("invite@example.com".toEmailAddress(), "secret123")
                    }
                }

                val updated = TestPostgres.db.transaction { context(ctx) { employees.byId(id) } }
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

            either {
                val emp1 =
                    TestPostgres.db.transaction {
                        context(ctx) { employees.new(id1, "Первый", null, null, null, emptyPermission) }
                    }
                TestPostgres.db.transaction {
                    context(ctx) { emp1.invite(email, "pass1") }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val emp2 =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) { employees.new(id2, "Второй", null, null, null, emptyPermission) }
                    }
                }.getOrElse { fail("Setup failed: $it") }

            val result =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) { emp2.invite(email, "pass2") }
                    }
                }
            assertEquals("LOGIN_TAKEN", assertIs<Either.Left<DomainError>>(result).value.code)
        }

    /** Возвращает [Employee] с теми же данными и новой настройкой прав [permissions]. */
    context(ctx: EmployeeRequestContext)
    private fun Employee.withNewPermissions(permissions: EmployeePermission): Employee = withNew(name, permissions, avatarId, phoneNo, email, availableBranches)
}
