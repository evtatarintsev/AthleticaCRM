package org.athletica.crm.domain.tasks

import arrow.core.getOrElse
import arrow.core.raise.context.Raise
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
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.core.tasks.TaskStatus
import org.athletica.crm.domain.employees.Employee
import org.athletica.crm.domain.employees.EmployeeBranchAccess
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail
import kotlin.time.Clock
import kotlin.time.Instant

/** Тесты операций над агрегатом задачи: status, assignTo, unassign, bulk-сценарии и проверки прав. */
class TaskOperationsTest {
    private val orgId = OrgId.new()
    private val userId = UserId.new()
    private val employeeId = EmployeeId.new()
    private val otherEmployeeId = EmployeeId.new()
    private val thirdEmployeeId = EmployeeId.new()
    private val branchId = BranchId.new()

    private val tasks = DbTasks()

    private fun ctx(
        id: EmployeeId = employeeId,
        permission: EmployeePermission = EmployeePermission(),
    ) = EmployeeRequestContext(
        lang = Lang.RU,
        userId = userId,
        orgId = orgId,
        branchId = branchId,
        employeeId = id,
        username = "test@example.com",
        clientIp = null,
        currency = Currency.RUB,
        permission = permission,
    )

    private val ctx = ctx()
    private val otherCtx = ctx(id = otherEmployeeId)
    private val ctxWithManage =
        ctx(permission = EmployeePermission(emptyList(), setOf(UserPermission.CAN_MANAGE_TASKS), emptySet()))

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .bind("id", orgId).bind("name", "Org").execute()
            TestPostgres.db.sql("INSERT INTO users (id, login, password_hash) VALUES (:id, :login, :hash)")
                .bind("id", userId).bind("login", "test@example.com").bind("hash", "hash").execute()
            TestPostgres.db.sql("INSERT INTO employees (id, org_id, name) VALUES (:id, :orgId, :name)")
                .bind("id", employeeId).bind("orgId", orgId).bind("name", "Иван").execute()
            TestPostgres.db.sql("INSERT INTO employees (id, org_id, name) VALUES (:id, :orgId, :name)")
                .bind("id", otherEmployeeId).bind("orgId", orgId).bind("name", "Пётр").execute()
            TestPostgres.db.sql("INSERT INTO employees (id, org_id, name) VALUES (:id, :orgId, :name)")
                .bind("id", thirdEmployeeId).bind("orgId", orgId).bind("name", "Анна").execute()
        }
    }

    private suspend fun taskStatusFromDb(taskId: TaskId): String =
        TestPostgres.db
            .sql("SELECT status FROM tasks WHERE id = :id")
            .bind("id", taskId)
            .firstOrNull { row -> row.asString("status") } ?: ""

    private suspend fun taskCompletedAt(taskId: TaskId): String? =
        TestPostgres.db
            .sql("SELECT COALESCE(completed_at::text, '') AS ca FROM tasks WHERE id = :id")
            .bind("id", taskId)
            .firstOrNull { row -> row.asString("ca") }
            ?.takeIf { it.isNotEmpty() }

    private fun employeeStub(id: EmployeeId): Employee =
        object : Employee {
            override val id = id
            override val userId = null
            override val name = "Stub"
            override val avatarId: UploadId? = null
            override val isOwner = false
            override val isActive = true
            override val joinedAt: Instant = Clock.System.now()
            override val permissions = EmployeePermission()
            override val phoneNo: String? = null
            override val email: EmailAddress? = null
            override val availableBranches: EmployeeBranchAccess = EmployeeBranchAccess.All

            context(ctx: EmployeeRequestContext, tr: Transaction)
            override suspend fun save() = Unit

            context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
            override suspend fun invite(email: EmailAddress, password: String) = Unit

            context(ctx: EmployeeRequestContext)
            override fun withNew(
                newName: String,
                newPermissions: EmployeePermission,
                newAvatarId: UploadId?,
                newPhoneNo: String?,
                newEmail: EmailAddress?,
                newAvailableBranches: EmployeeBranchAccess,
            ): Employee = this
        }

    // ─── массовая смена статуса ───────────────────────────────────────────────

    @Test
    fun `status меняет статус у нескольких задач`() =
        runTest {
            val id1 = TaskId.new()
            val id2 = TaskId.new()
            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        tasks.new(id1, "Задача 1", "", null, null, null)
                        tasks.new(id2, "Задача 2", "", null, null, null)
                    }
                }
            }.getOrElse { fail("Setup: $it") }

            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        tasks.byIds(listOf(id1, id2))
                            .map { context(ctx) { it.status(TaskStatus.IN_PROGRESS) } }
                            .forEach { it.save() }
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            assertEquals("IN_PROGRESS", taskStatusFromDb(id1))
            assertEquals("IN_PROGRESS", taskStatusFromDb(id2))
        }

    @Test
    fun `status COMPLETED проставляет completedAt у нескольких задач`() =
        runTest {
            val id1 = TaskId.new()
            val id2 = TaskId.new()
            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        tasks.new(id1, "Задача 1", "", null, null, null)
                        tasks.new(id2, "Задача 2", "", null, null, null)
                    }
                }
            }.getOrElse { fail("Setup: $it") }

            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        tasks.byIds(listOf(id1, id2))
                            .map { context(ctx) { it.status(TaskStatus.COMPLETED) } }
                            .forEach { it.save() }
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            assertNotNull(taskCompletedAt(id1))
            assertNotNull(taskCompletedAt(id2))
        }

    // ─── массовое переназначение исполнителя ──────────────────────────────────

    @Test
    fun `assignTo переназначает исполнителя у нескольких задач`() =
        runTest {
            val id1 = TaskId.new()
            val id2 = TaskId.new()
            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        tasks.new(id1, "Задача 1", "", null, null, null)
                        tasks.new(id2, "Задача 2", "", null, null, null)
                    }
                }
            }.getOrElse { fail("Setup: $it") }

            val thirdEmployee = employeeStub(thirdEmployeeId)
            either {
                TestPostgres.db.transaction {
                    context(ctxWithManage) {
                        tasks.byIds(listOf(id1, id2))
                            .map { context(ctxWithManage) { it.assignTo(thirdEmployee) } }
                            .forEach { it.save() }
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val loaded1 =
                either {
                    TestPostgres.db.transaction { context(ctx) { tasks.byId(id1) } }
                }.getOrElse { fail("Load: $it") }
            val loaded2 =
                either {
                    TestPostgres.db.transaction { context(ctx) { tasks.byId(id2) } }
                }.getOrElse { fail("Load: $it") }

            assertEquals(thirdEmployeeId, loaded1.assigneeId)
            assertEquals(thirdEmployeeId, loaded2.assigneeId)
        }

    @Test
    fun `unassign снимает исполнителя`() =
        runTest {
            val id1 = TaskId.new()
            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        tasks.new(id1, "Задача", "", null, null, null)
                    }
                }
            }.getOrElse { fail("Setup: $it") }

            val otherEmployee = employeeStub(otherEmployeeId)
            either {
                TestPostgres.db.transaction {
                    context(ctxWithManage) {
                        val assigned = context(ctxWithManage) { tasks.byId(id1).assignTo(otherEmployee) }
                        assigned.save()
                    }
                }
            }.getOrElse { fail("Setup assign: $it") }

            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        val unassigned = context(ctx) { tasks.byId(id1).unassign() }
                        unassigned.save()
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val loaded =
                either {
                    TestPostgres.db.transaction { context(ctx) { tasks.byId(id1) } }
                }.getOrElse { fail("Load: $it") }

            assertNull(loaded.assigneeId)
        }

    // ─── проверка прав ────────────────────────────────────────────────────────

    @Test
    fun `status без прав отклоняет всю операцию если хоть одна задача чужая`() =
        runTest {
            val myTaskId = TaskId.new()
            val foreignTaskId = TaskId.new()
            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        tasks.new(myTaskId, "Моя задача", "", null, null, null)
                    }
                }
            }.getOrElse { fail("Setup: $it") }
            either {
                TestPostgres.db.transaction {
                    context(otherCtx) {
                        tasks.new(foreignTaskId, "Чужая задача", "", null, null, null)
                    }
                }
            }.getOrElse { fail("Setup: $it") }

            val result =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) {
                            tasks.byIds(listOf(myTaskId, foreignTaskId))
                                .map { context(ctx) { it.status(TaskStatus.IN_PROGRESS) } }
                                .forEach { it.save() }
                        }
                    }
                }

            assertIs<arrow.core.Either.Left<DomainError>>(result)
            assertEquals("PERMISSION_DENIED", result.value.code)
            assertEquals("PENDING", taskStatusFromDb(myTaskId))
            assertEquals("PENDING", taskStatusFromDb(foreignTaskId))
        }

    @Test
    fun `status с CAN_MANAGE_TASKS разрешает редактировать чужие задачи`() =
        runTest {
            val foreignTaskId = TaskId.new()
            either {
                TestPostgres.db.transaction {
                    context(otherCtx) {
                        tasks.new(foreignTaskId, "Чужая задача", "", null, null, null)
                    }
                }
            }.getOrElse { fail("Setup: $it") }

            either {
                TestPostgres.db.transaction {
                    context(ctxWithManage) {
                        tasks.byIds(listOf(foreignTaskId))
                            .map { context(ctxWithManage) { it.status(TaskStatus.COMPLETED) } }
                            .forEach { it.save() }
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            assertEquals("COMPLETED", taskStatusFromDb(foreignTaskId))
        }
}
