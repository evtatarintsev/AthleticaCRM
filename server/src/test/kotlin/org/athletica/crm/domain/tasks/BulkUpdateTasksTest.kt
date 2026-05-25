package org.athletica.crm.domain.tasks

import arrow.core.getOrElse
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.core.tasks.TaskStatus
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.routes.tasks.applyBulk
import org.athletica.crm.storage.asString
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

class BulkUpdateTasksTest {
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

    // ─── массовая смена статуса ───────────────────────────────────────────────

    @Test
    fun `applyBulk меняет статус у нескольких задач`() =
        runTest {
            val id1 = TaskId.new()
            val id2 = TaskId.new()
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        tasks.new(id1, "Задача 1", "", null, null, null, null, emptyList())
                        tasks.new(id2, "Задача 2", "", null, null, null, null, emptyList())
                    }
                }
            }.getOrElse { fail("Setup: $it") }

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        tasks.applyBulk(listOf(id1, id2)) { status = TaskStatus.IN_PROGRESS }
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            assertEquals("IN_PROGRESS", taskStatusFromDb(id1))
            assertEquals("IN_PROGRESS", taskStatusFromDb(id2))
        }

    @Test
    fun `applyBulk переход в COMPLETED проставляет completedAt`() =
        runTest {
            val id1 = TaskId.new()
            val id2 = TaskId.new()
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        tasks.new(id1, "Задача 1", "", null, null, null, null, emptyList())
                        tasks.new(id2, "Задача 2", "", null, null, null, null, emptyList())
                    }
                }
            }.getOrElse { fail("Setup: $it") }

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        tasks.applyBulk(listOf(id1, id2)) { status = TaskStatus.COMPLETED }
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            assertNotNull(taskCompletedAt(id1))
            assertNotNull(taskCompletedAt(id2))
        }

    // ─── массовое переназначение исполнителя ──────────────────────────────────

    @Test
    fun `applyBulk переназначает исполнителя у нескольких задач`() =
        runTest {
            val id1 = TaskId.new()
            val id2 = TaskId.new()
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        tasks.new(id1, "Задача 1", "", null, null, null, null, emptyList())
                        tasks.new(id2, "Задача 2", "", null, null, null, null, emptyList())
                    }
                }
            }.getOrElse { fail("Setup: $it") }

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        tasks.applyBulk(listOf(id1, id2)) { assigneeId = thirdEmployeeId }
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val loaded1 =
                either<DomainError, _> {
                    TestPostgres.db.transaction { context(ctx, this) { tasks.byId(id1) } }
                }.getOrElse { fail("Load: $it") }
            val loaded2 =
                either<DomainError, _> {
                    TestPostgres.db.transaction { context(ctx, this) { tasks.byId(id2) } }
                }.getOrElse { fail("Load: $it") }

            assertEquals(thirdEmployeeId, loaded1.assigneeId)
            assertEquals(thirdEmployeeId, loaded2.assigneeId)
        }

    @Test
    fun `applyBulk снимает назначение (null)`() =
        runTest {
            val id1 = TaskId.new()
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        tasks.new(id1, "Задача", "", otherEmployeeId, null, null, null, emptyList())
                    }
                }
            }.getOrElse { fail("Setup: $it") }

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        tasks.applyBulk(listOf(id1)) { assigneeId = null }
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val loaded =
                either<DomainError, _> {
                    TestPostgres.db.transaction { context(ctx, this) { tasks.byId(id1) } }
                }.getOrElse { fail("Load: $it") }

            assertNull(loaded.assigneeId)
        }

    // ─── проверка прав ────────────────────────────────────────────────────────

    @Test
    fun `applyBulk без прав отклоняет всю операцию, если хоть одна чужая задача`() =
        runTest {
            val myTaskId = TaskId.new()
            val foreignTaskId = TaskId.new()
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        tasks.new(myTaskId, "Моя задача", "", null, null, null, null, emptyList())
                    }
                }
            }.getOrElse { fail("Setup: $it") }
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(otherCtx, this) {
                        tasks.new(foreignTaskId, "Чужая задача", "", null, null, null, null, emptyList())
                    }
                }
            }.getOrElse { fail("Setup: $it") }

            val result =
                either<DomainError, Unit> {
                    TestPostgres.db.transaction {
                        context(ctx, this) {
                            tasks.applyBulk(listOf(myTaskId, foreignTaskId)) { status = TaskStatus.IN_PROGRESS }
                        }
                    }
                }

            assertIs<arrow.core.Either.Left<DomainError>>(result)
            assertEquals("PERMISSION_DENIED", result.value.code)
            assertEquals("PENDING", taskStatusFromDb(myTaskId))
            assertEquals("PENDING", taskStatusFromDb(foreignTaskId))
        }

    @Test
    fun `applyBulk с CAN_MANAGE_TASKS разрешает редактировать чужие задачи`() =
        runTest {
            val foreignTaskId = TaskId.new()
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(otherCtx, this) {
                        tasks.new(foreignTaskId, "Чужая задача", "", null, null, null, null, emptyList())
                    }
                }
            }.getOrElse { fail("Setup: $it") }

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctxWithManage, this) {
                        tasks.applyBulk(listOf(foreignTaskId)) { status = TaskStatus.COMPLETED }
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            assertEquals("COMPLETED", taskStatusFromDb(foreignTaskId))
        }
}
