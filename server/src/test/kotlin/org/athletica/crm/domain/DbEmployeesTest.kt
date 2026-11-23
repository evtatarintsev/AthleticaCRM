package org.athletica.crm.domain

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.Raise
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
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.core.toEmailAddress
import org.athletica.crm.domain.auth.DbUsers
import org.athletica.crm.domain.employees.DbEmployee
import org.athletica.crm.domain.employees.DbEmployees
import org.athletica.crm.domain.employees.DbRoles
import org.athletica.crm.domain.employees.Employee
import org.athletica.crm.domain.employees.EmployeeAccess
import org.athletica.crm.domain.employees.EmployeeBranchAccess
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.domain.employees.EmployeePermissionSelection
import org.athletica.crm.domain.employees.EmployeeRole
import org.athletica.crm.employeeContext
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.storage.QueryBuilder
import org.athletica.crm.storage.Transaction
import org.athletica.crm.testName
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

    private val emptyPermission = EmployeePermissionSelection()

    @Before
    fun setUp() {
        orThrow {
            TestPostgres.truncate()
            runBlocking {
                TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                    .bind("id", orgId).bind("name", "Org 1").execute()
                TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                    .bind("id", otherOrgId).bind("name", "Org 2").execute()
            }
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
                            employees.new(id, testName("Иван Иванов"), "+71234567890", "ivan@example.com".toEmailAddress(), null, emptyPermission)
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
                            employees.new(id, testName("Анна"), null, null, null, emptyPermission)
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
                    context(ctx) { employees.new(id, testName("Первый"), null, null, null, emptyPermission) }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val result =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) { employees.new(id, testName("Второй"), null, null, null, emptyPermission) }
                    }
                }
            assertEquals("EMPLOYEE_ALREADY_EXISTS", assertIs<Either.Left<AppError>>(result).value.code)
        }

    // ─── byId ─────────────────────────────────────────────────────────────────

    @Test
    fun `byId возвращает сотрудника по id`() =
        runTest {
            val id = EmployeeId.new()
            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        employees.new(id, testName("Пётр Петров"), "89001234567", "petr@example.com".toEmailAddress(), null, emptyPermission)
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
            assertEquals("EMPLOYEE_NOT_FOUND", assertIs<Either.Left<AppError>>(result).value.code)
        }

    @Test
    fun `byId возвращает EMPLOYEE_NOT_FOUND для id из другой организации`() =
        runTest {
            val id = EmployeeId.new()
            either {
                TestPostgres.db.transaction {
                    context(ctx) { employees.new(id, testName("Сотрудник"), null, null, null, emptyPermission) }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val result =
                either {
                    TestPostgres.db.transaction { context(otherCtx) { employees.byId(id) } }
                }
            assertEquals("EMPLOYEE_NOT_FOUND", assertIs<Either.Left<AppError>>(result).value.code)
        }

    @Test
    fun `byId загружает права ролей сотрудника`() =
        orThrow {
            runTest {
                val id = EmployeeId.new()
                val roleId = Uuid.random()

                either {
                    TestPostgres.db.transaction {
                        context(ctx) {
                            employees.new(id, testName("Менеджер"), null, null, null, emptyPermission)
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
        }

    @Test
    fun `byId - сотрудник без ролей и выдач не имеет прав`() =
        runTest {
            val id = EmployeeId.new()
            either {
                TestPostgres.db.transaction {
                    context(ctx) { employees.new(id, testName("Без ролей"), null, null, null, emptyPermission) }
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
                val role = EmployeeRole(Uuid.random(), testName("Кассир"), setOf(UserPermission.CAN_VIEW_CLIENT_BALANCE, UserPermission.CAN_MANAGE_ORG_BALANCE))
                TestPostgres.db.transaction {
                    context(ctx) {
                        roles.new(role)
                        employees.new(
                            id,
                            testName("Кассир"),
                            null,
                            null,
                            null,
                            EmployeePermissionSelection(
                                roleIds = listOf(role.id),
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

    @Test
    fun `byId - владелец без ролей и выдач имеет все права`() =
        runTest {
            val id = EmployeeId.new()
            either {
                TestPostgres.db.transaction {
                    context(ctx) { employees.new(id, testName("Владелец"), null, null, null, emptyPermission) }
                }
            }.getOrElse { fail("Setup failed: $it") }
            markOwner(id)

            val employee =
                either {
                    TestPostgres.db.transaction { context(ctx) { employees.byId(id) } }
                }.getOrElse { fail("Unexpected error: $it") }

            assertTrue(employee.isOwner)
            assertTrue(UserPermission.entries.all { employee.hasPermission(it) })
        }

    @Test
    fun `byId - персональный отзыв у владельца не действует`() =
        runTest {
            val id = EmployeeId.new()
            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        employees.new(
                            id,
                            testName("Владелец"),
                            null,
                            null,
                            null,
                            EmployeePermissionSelection(
                                roleIds = emptyList(),
                                grantedPermissions = emptySet(),
                                revokedPermissions = setOf(UserPermission.CAN_MANAGE_TASKS),
                            ),
                        )
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }
            markOwner(id)

            val employee =
                either {
                    TestPostgres.db.transaction { context(ctx) { employees.byId(id) } }
                }.getOrElse { fail("Unexpected error: $it") }

            assertTrue(employee.hasPermission(UserPermission.CAN_MANAGE_TASKS))
        }

    @Test
    fun `byId - обычный сотрудник имеет только права ролей за вычетом отзывов`() =
        runTest {
            val id = EmployeeId.new()
            either {
                val role = EmployeeRole(Uuid.random(), testName("Кассир"), setOf(UserPermission.CAN_VIEW_CLIENT_BALANCE, UserPermission.CAN_MANAGE_ORG_BALANCE))
                TestPostgres.db.transaction {
                    context(ctx) {
                        roles.new(role)
                        employees.new(
                            id,
                            testName("Кассир"),
                            null,
                            null,
                            null,
                            EmployeePermissionSelection(
                                roleIds = listOf(role.id),
                                grantedPermissions = emptySet(),
                                revokedPermissions = setOf(UserPermission.CAN_MANAGE_ORG_BALANCE),
                            ),
                        )
                    }
                }

                val employee = TestPostgres.db.transaction { context(ctx) { employees.byId(id) } }
                assertFalse(employee.isOwner)
                assertEquals(
                    setOf(UserPermission.CAN_VIEW_CLIENT_BALANCE),
                    UserPermission.entries.filterTo(mutableSetOf()) { employee.hasPermission(it) },
                )
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `byIds загружает сотрудников одним SQL, пустой набор не обращается к БД`() =
        runTest {
            val first = EmployeeId.new()
            val second = EmployeeId.new()
            either {
                val role = EmployeeRole(Uuid.random(), testName("Тренер"), setOf(UserPermission.CAN_MANAGE_TASKS))
                TestPostgres.db.transaction {
                    context(ctx) {
                        roles.new(role)
                        employees.new(first, testName("Первый"), null, null, null, EmployeePermissionSelection(listOf(role.id), setOf(UserPermission.CAN_VIEW_ALL_TASKS)))
                        employees.new(second, testName("Второй"), null, null, null, emptyPermission, EmployeeBranchAccess.Selected(emptyList()))
                    }
                }

                TestPostgres.db.transaction {
                    val counting = CountingTransaction(this)
                    context(ctx, counting) {
                        assertEquals(2, employees.byIds(listOf(first, second, first)).size)
                        assertEquals(1, counting.statements)
                        assertEquals(emptyList(), employees.byIds(emptyList()))
                        assertEquals(1, counting.statements)
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `byIds не смешивает роли, права и филиалы разных сотрудников`() =
        runTest {
            val first = EmployeeId.new()
            val second = EmployeeId.new()
            val third = EmployeeId.new()
            val branchA = BranchId.new()
            val branchB = BranchId.new()
            val manager = EmployeeRole(Uuid.random(), testName("Менеджер"), setOf(UserPermission.CAN_MANAGE_TASKS))
            val empty = EmployeeRole(Uuid.random(), testName("Без прав"), emptySet())
            val trainer = EmployeeRole(Uuid.random(), testName("Тренер"), setOf(UserPermission.CAN_VIEW_ALL_TASKS))
            either {
                TestPostgres.db.transaction {
                    listOf(branchA, branchB).forEach {
                        sql("INSERT INTO branches (id, org_id, name) VALUES (:id, :orgId, :name)")
                            .bind("id", it)
                            .bind("orgId", orgId)
                            .bind("name", "Филиал $it")
                            .execute()
                    }
                    context(ctx) {
                        listOf(manager, empty, trainer).forEach { roles.new(it) }
                        employees.new(
                            first,
                            testName("Первый"),
                            null,
                            null,
                            null,
                            EmployeePermissionSelection(listOf(manager.id, empty.id), setOf(UserPermission.CAN_VIEW_CLIENT_BALANCE)),
                            EmployeeBranchAccess.Selected(listOf(branchA, branchB)),
                        )
                        employees.new(second, testName("Второй"), null, null, null, EmployeePermissionSelection(listOf(trainer.id)), EmployeeBranchAccess.Selected(emptyList()))
                        employees.new(third, testName("Третий"), null, null, null, emptyPermission, EmployeeBranchAccess.All)
                    }
                }

                val loaded = TestPostgres.db.transaction { context(ctx) { employees.byIds(listOf(first, second, third, second)) } }
                assertEquals(listOf(first, second, third).sortedBy { it.toString() }, loaded.map { it.id }.sortedBy { it.toString() })
                val byId = loaded.associateBy { it.id }

                val firstEmployee = byId.getValue(first)
                assertEquals(setOf(manager, empty), firstEmployee.roles.toSet())
                assertEquals(setOf(branchA, branchB), assertIs<EmployeeBranchAccess.Selected>(firstEmployee.availableBranches).ids.toSet())
                assertTrue(firstEmployee.hasPermission(UserPermission.CAN_MANAGE_TASKS))
                assertTrue(firstEmployee.hasPermission(UserPermission.CAN_VIEW_CLIENT_BALANCE))
                assertFalse(firstEmployee.hasPermission(UserPermission.CAN_VIEW_ALL_TASKS))

                val secondEmployee = byId.getValue(second)
                assertEquals(listOf(trainer), secondEmployee.roles)
                assertEquals(EmployeeBranchAccess.Selected(emptyList()), secondEmployee.availableBranches)
                assertTrue(secondEmployee.hasPermission(UserPermission.CAN_VIEW_ALL_TASKS))
                assertFalse(secondEmployee.hasPermission(UserPermission.CAN_MANAGE_TASKS))
                assertFalse(secondEmployee.hasPermission(UserPermission.CAN_VIEW_CLIENT_BALANCE))

                val thirdEmployee = byId.getValue(third)
                assertEquals(emptyList(), thirdEmployee.roles)
                assertEquals(EmployeeBranchAccess.All, thirdEmployee.availableBranches)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `byIds возвращает EMPLOYEE_NOT_FOUND для частично найденного набора`() =
        runTest {
            val id = EmployeeId.new()
            either {
                TestPostgres.db.transaction {
                    context(ctx) { employees.new(id, testName("Сотрудник"), null, null, null, emptyPermission) }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val result =
                either {
                    TestPostgres.db.transaction { context(ctx) { employees.byIds(listOf(id, EmployeeId.new(), id)) } }
                }
            assertEquals("EMPLOYEE_NOT_FOUND", assertIs<Either.Left<AppError>>(result).value.code)
        }

    // ─── save ─────────────────────────────────────────────────────────────────

    @Test
    fun `save обновляет поля сотрудника в БД`() =
        runTest {
            val id = EmployeeId.new()
            either {
                val employee =
                    TestPostgres.db.transaction {
                        context(ctx) { employees.new(id, testName("Старое имя"), null, null, null, emptyPermission) }
                    }

                TestPostgres.db.transaction {
                    context(this, ctx) {
                        (employee as DbEmployee)
                            .copy(name = "Новое имя", phoneNo = "+79001112233")
                            .save()
                    }
                }

                val updated = TestPostgres.db.transaction { context(ctx) { employees.byId(id) } }
                assertEquals("Новое имя", updated.name)
                assertEquals("+79001112233", updated.phoneNo)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `save обновляет email сотрудника`() =
        runTest {
            val id = EmployeeId.new()
            either {
                val employee =
                    TestPostgres.db.transaction {
                        context(ctx) { employees.new(id, testName("Сотрудник"), null, null, null, emptyPermission) }
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
                val trainer = EmployeeRole(Uuid.random(), testName("Тренер"), setOf(UserPermission.CAN_VIEW_ALL_TASKS))
                val manager = EmployeeRole(Uuid.random(), testName("Менеджер"), setOf(UserPermission.CAN_MANAGE_TASKS))
                TestPostgres.db.transaction {
                    context(ctx) {
                        roles.new(trainer)
                        roles.new(manager)
                    }
                }

                val permission = EmployeePermissionSelection(roleIds = listOf(trainer.id, manager.id), grantedPermissions = emptySet(), revokedPermissions = emptySet())
                val employee =
                    TestPostgres.db.transaction {
                        context(ctx) { employees.new(id, testName("Сотрудник с ролями"), null, null, null, permission) }
                    }

                var retrieved = TestPostgres.db.transaction { context(ctx) { employees.byId(id) } }
                assertTrue(retrieved.hasPermission(UserPermission.CAN_VIEW_ALL_TASKS))
                assertTrue(retrieved.hasPermission(UserPermission.CAN_MANAGE_TASKS))

                val updatedPermission = EmployeePermissionSelection(roleIds = listOf(manager.id), grantedPermissions = emptySet(), revokedPermissions = emptySet())
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
                val first = EmployeeRole(Uuid.random(), testName("Роль 1"), setOf(UserPermission.CAN_VIEW_ALL_TASKS))
                val second = EmployeeRole(Uuid.random(), testName("Роль 2"), setOf(UserPermission.CAN_MANAGE_TASKS))
                val third = EmployeeRole(Uuid.random(), testName("Роль 3"), setOf(UserPermission.CAN_VIEW_CLIENT_BALANCE))
                TestPostgres.db.transaction {
                    context(ctx) {
                        roles.new(first)
                        roles.new(second)
                        roles.new(third)
                    }
                }

                val initialPermission = EmployeePermissionSelection(roleIds = listOf(first.id, second.id), grantedPermissions = emptySet(), revokedPermissions = emptySet())
                val employee =
                    TestPostgres.db.transaction {
                        context(ctx) { employees.new(id, testName("Сотрудник"), null, null, null, initialPermission) }
                    }

                val newPermission =
                    EmployeePermissionSelection(
                        roleIds = listOf(third.id),
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
                        context(ctx) { employees.new(id, testName("Новый сотрудник"), null, null, null, emptyPermission) }
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
                        context(ctx) { employees.new(id1, testName("Первый"), null, null, null, emptyPermission) }
                    }
                TestPostgres.db.transaction {
                    context(ctx) { emp1.invite(email, "pass1") }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val emp2 =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) { employees.new(id2, testName("Второй"), null, null, null, emptyPermission) }
                    }
                }.getOrElse { fail("Setup failed: $it") }

            val result =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) { emp2.invite(email, "pass2") }
                    }
                }
            assertEquals("LOGIN_TAKEN", assertIs<Either.Left<AppError>>(result).value.code)
        }

    /** Делает сотрудника [id] владельцем организации, как это делает регистрация. */
    private suspend fun markOwner(id: EmployeeId) {
        orThrow {
            TestPostgres.db
                .sql("UPDATE employees SET is_owner = true WHERE id = :id")
                .bind("id", id)
                .execute()
        }
    }

    @Test
    fun `чужие назначения отклоняются целиком при создании и изменении`() =
        runTest {
            val id = EmployeeId.new()
            val newId = EmployeeId.new()
            val ownRole = EmployeeRole(Uuid.random(), testName("Своя роль"), setOf(UserPermission.CAN_MANAGE_TASKS))
            val foreignRole = EmployeeRole(Uuid.random(), testName("Чужая роль"), emptySet())
            val foreignBranch = BranchId.new()
            orThrow {
                TestPostgres.db.transaction {
                    context(otherCtx) { roles.new(foreignRole) }
                    sql("INSERT INTO branches (id, org_id, name) VALUES (:id, :orgId, 'Чужой филиал')")
                        .bind("id", foreignBranch)
                        .bind("orgId", otherOrgId)
                        .execute()
                    context(ctx) {
                        roles.new(ownRole)
                        employees.new(id, testName("Сотрудник"), null, null, null, EmployeePermissionSelection(listOf(ownRole.id)))
                    }
                }
            }

            val noBranches = EmployeeBranchAccess.Selected(emptyList())
            for ((code, selection, branches) in listOf(
                Triple("ROLE_NOT_FOUND", EmployeePermissionSelection(listOf(ownRole.id, foreignRole.id)), noBranches),
                Triple("BRANCH_NOT_FOUND", emptyPermission, EmployeeBranchAccess.Selected(listOf(foreignBranch))),
            )) {
                val created =
                    either {
                        TestPostgres.db.transaction {
                            context(ctx) { employees.new(newId, testName("Новый"), null, null, null, selection, branches) }
                        }
                    }
                assertEquals(code, assertIs<Either.Left<AppError>>(created).value.code)
                val updated =
                    either {
                        TestPostgres.db.transaction {
                            context(ctx) {
                                employees.byId(id).withNew(testName("Новое имя"), selection, null, null, null, branches).save()
                            }
                        }
                    }
                assertEquals(code, assertIs<Either.Left<AppError>>(updated).value.code)
            }

            orThrow {
                TestPostgres.db.transaction {
                    context(ctx) {
                        val employee = employees.byId(id)
                        assertEquals("Сотрудник", employee.name)
                        assertEquals(listOf(ownRole), employee.roles)
                        assertEquals(EmployeeBranchAccess.All, employee.availableBranches)

                        val created = employees.new(newId, testName("Новый"), null, null, null, emptyPermission, noBranches)
                        assertEquals(emptyList(), created.roles)
                        assertEquals(noBranches, created.availableBranches)
                        employee.withNew(testName(employee.name), emptyPermission, null, null, null, noBranches).save()
                        assertEquals(emptyList(), employees.byId(id).roles)
                        assertEquals(noBranches, employees.byId(id).availableBranches)
                    }
                }
            }
        }

    /** Возвращает [Employee] с теми же данными и новой настройкой прав [permissions]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    private suspend fun Employee.withNewPermissions(permissions: EmployeePermissionSelection): Employee = withNew(testName(name), permissions, avatarId, phoneNo, email, availableBranches)

    /** Считает вызовы [Transaction.sql], делегируя их транзакции [delegate]. */
    private class CountingTransaction(private val delegate: Transaction) : Transaction {
        /** Число SQL-запросов, построенных через эту транзакцию. */
        var statements = 0
            private set

        override fun sql(sql: String): QueryBuilder {
            statements++
            return delegate.sql(sql)
        }
    }

    // ─── block / unblock ──────────────────────────────────────────────────────

    /** Создаёт сотрудника [id] и выдаёт ему доступ с логином [login]. */
    private suspend fun invitedEmployee(id: EmployeeId, login: String): Employee =
        either {
            TestPostgres.db.transaction {
                context(ctx) {
                    employees.new(id, testName("Сотрудник"), null, null, null, emptyPermission).invite(login.toEmailAddress(), "secret123")
                }
            }
            TestPostgres.db.transaction { context(ctx) { employees.byId(id) } }
        }.getOrElse { fail("Unexpected error: $it") }

    /** Выполняет [block] в транзакции под контекстом [ctx] и возвращает результат либо ошибку. */
    private suspend fun <T> attempt(block: suspend context(EmployeeRequestContext, Transaction, Raise<AppError>) () -> T): Either<AppError, T> =
        arrow.core.raise.either {
            val raise: Raise<AppError> = this
            TestPostgres.db.transaction { block(ctx, this, raise) }
        }

    @Test
    fun `block блокирует сотрудника, повторная блокировка не меняет дату`() =
        runTest {
            val id = EmployeeId.new()
            invitedEmployee(id, "block@example.com")

            assertIs<Either.Right<Unit>>(attempt { employees.byId(id).block() })
            val blocked = assertIs<Either.Right<Employee>>(attempt { employees.byId(id) }).value
            val blockedAt = assertIs<EmployeeAccess.Blocked>(blocked.access).blockedAt
            assertFalse(blocked.isActive)

            assertIs<Either.Right<Unit>>(attempt { employees.byId(id).block() })
            assertEquals(blockedAt, assertIs<EmployeeAccess.Blocked>(assertIs<Either.Right<Employee>>(attempt { employees.byId(id) }).value.access).blockedAt)
        }

    @Test
    fun `block владельца отклоняется`() =
        runTest {
            val id = EmployeeId.new()
            invitedEmployee(id, "owner-block@example.com")
            markOwner(id)

            val result = attempt { employees.byId(id).block() }

            assertEquals("EMPLOYEE_OWNER_CANNOT_BE_BLOCKED", assertIs<Either.Left<AppError>>(result).value.code)
            assertIs<EmployeeAccess.Active>(assertIs<Either.Right<Employee>>(attempt { employees.byId(id) }).value.access)
        }

    @Test
    fun `block самого себя отклоняется`() =
        runTest {
            invitedEmployee(employeeId, "self-block@example.com")

            val result = attempt { employees.byId(employeeId).block() }

            assertEquals("EMPLOYEE_CANNOT_BLOCK_SELF", assertIs<Either.Left<AppError>>(result).value.code)
        }

    @Test
    fun `unblock снимает блокировку`() =
        runTest {
            val id = EmployeeId.new()
            invitedEmployee(id, "unblock@example.com")
            assertIs<Either.Right<Unit>>(attempt { employees.byId(id).block() })

            assertIs<Either.Right<Unit>>(attempt { employees.byId(id).unblock() })

            val employee = assertIs<Either.Right<Employee>>(attempt { employees.byId(id) }).value
            assertIs<EmployeeAccess.Active>(employee.access)
            assertTrue(employee.isActive)
        }

    @Test
    fun `заблокированного без логина нельзя пригласить, после снятия блокировки — можно`() =
        runTest {
            val id = EmployeeId.new()
            assertIs<Either.Right<Employee>>(attempt { employees.new(id, testName("Уволенный"), null, null, null, emptyPermission) })
            assertIs<Either.Right<Unit>>(attempt { employees.byId(id).block() })
            assertEquals(EmployeeAccess.Blocked::class, assertIs<Either.Right<Employee>>(attempt { employees.byId(id) }).value.access::class)

            val result = attempt { employees.byId(id).invite("blocked@example.com".toEmailAddress(), "secret123") }

            assertEquals("EMPLOYEE_BLOCKED", assertIs<Either.Left<AppError>>(result).value.code)
            assertIs<Either.Right<Unit>>(attempt { employees.byId(id).unblock() })
            assertEquals(EmployeeAccess.NoLogin, assertIs<Either.Right<Employee>>(attempt { employees.byId(id) }).value.access)
            assertIs<Either.Right<Unit>>(attempt { employees.byId(id).invite("blocked@example.com".toEmailAddress(), "secret123") })
        }

    @Test
    fun `заблокированного с логином нельзя пригласить повторно`() =
        runTest {
            val id = EmployeeId.new()
            invitedEmployee(id, "blocked-invited@example.com")
            assertIs<Either.Right<Unit>>(attempt { employees.byId(id).block() })

            val result = attempt { employees.byId(id).invite("blocked-again@example.com".toEmailAddress(), "secret123") }

            assertEquals("EMPLOYEE_BLOCKED", assertIs<Either.Left<AppError>>(result).value.code)
        }

    @Test
    fun `повторная выдача доступа отклоняется`() =
        runTest {
            val id = EmployeeId.new()
            val employee = invitedEmployee(id, "twice@example.com")

            val result = attempt { employee.invite("twice-again@example.com".toEmailAddress(), "secret123") }

            assertEquals("EMPLOYEE_ACCESS_ALREADY_SENT", assertIs<Either.Left<AppError>>(result).value.code)
        }

    @Test
    fun `логин нельзя привязать ко второму сотруднику`() =
        runTest {
            val employee = invitedEmployee(EmployeeId.new(), "single@example.com")

            val result =
                either {
                    TestPostgres.db
                        .sql("INSERT INTO employees (id, org_id, user_id, name) VALUES (:id, :orgId, :userId, 'Двойник')")
                        .bind("id", EmployeeId.new())
                        .bind("orgId", otherOrgId)
                        .bind("userId", employee.userId)
                        .execute()
                }

            assertIs<Either.Left<AppError>>(result)
        }
}
