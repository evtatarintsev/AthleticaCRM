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
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.entityids.toUploadId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.core.tasks.TaskStatus
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.storage.asLong
import org.athletica.crm.storage.asString
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.uuid.Uuid

class DbTasksTest {
    private val orgId = OrgId.new()
    private val userId = UserId.new()
    private val employeeId = EmployeeId.new()
    private val otherEmployeeId = EmployeeId.new()
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
    private val ctxManage = ctx(permission = EmployeePermission(emptyList(), setOf(UserPermission.CAN_MANAGE_TASKS), emptySet()))
    private val otherCtx = ctx(id = otherEmployeeId)
    private val ctxWithViewAll = ctx(permission = EmployeePermission(emptyList(), setOf(UserPermission.CAN_VIEW_ALL_TASKS), emptySet()))

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
        }
    }

    private suspend fun insertUpload(): UploadId {
        val id = Uuid.generateV7()
        TestPostgres.db.sql(
            """
            INSERT INTO uploads (id, org_id, uploaded_by, object_key, original_name, content_type, size_bytes)
            VALUES (:id, :orgId, :userId, :key, :name, 'application/pdf', 1024)
            """.trimIndent(),
        )
            .bind("id", id).bind("orgId", orgId).bind("userId", userId)
            .bind("key", "uploads/$id").bind("name", "file.pdf")
            .execute()
        return id.toUploadId()
    }

    private suspend fun countAttachments(taskId: TaskId): Long =
        TestPostgres.db
            .sql("SELECT COUNT(*) FROM task_attachments WHERE task_id = :id")
            .bind("id", taskId)
            .firstOrNull { row -> row.asLong(0) } ?: 0L

    private suspend fun taskCompletedAt(taskId: TaskId): String? =
        TestPostgres.db
            .sql("SELECT COALESCE(completed_at::text, '') AS ca FROM tasks WHERE id = :id")
            .bind("id", taskId)
            .firstOrNull { row -> row.asString("ca") }
            ?.takeIf { it.isNotEmpty() }

    // ─── new() ────────────────────────────────────────────────────────────────

    @Test
    fun `new создаёт задачу в базе данных`() =
        runTest {
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        tasks.new(TaskId.new(), "Тест", "", null, null, null)
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val count =
                TestPostgres.db
                    .sql("SELECT COUNT(*) FROM tasks")
                    .firstOrNull { row -> row.asLong(0) } ?: 0L
            assertEquals(1L, count)
        }

    @Test
    fun `new создаёт задачу со статусом PENDING без исполнителя и вложений`() =
        runTest {
            val task =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) {
                            tasks.new(TaskId.new(), "Задача", "Описание", null, null, null)
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(TaskStatus.PENDING, task.status)
            assertEquals("Задача", task.title)
            assertEquals("Описание", task.description)
            assertEquals(employeeId, task.createdBy)
            assertNull(task.assigneeId)
            assertTrue(task.attachments.isEmpty())
        }

    // ─── status() / completedAt ───────────────────────────────────────────────

    @Test
    fun `status COMPLETED проставляет completedAt`() =
        runTest {
            val task =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) {
                            tasks.new(TaskId.new(), "Задача", "", null, null, null)
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertNull(taskCompletedAt(task.id))

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        val loaded = tasks.byId(task.id)
                        val updated = context(ctx) { loaded.status(TaskStatus.COMPLETED) }
                        updated.save()
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            assertNotNull(taskCompletedAt(task.id))
        }

    @Test
    fun `status возврат из COMPLETED сбрасывает completedAt`() =
        runTest {
            val task =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) {
                            tasks.new(TaskId.new(), "Задача", "", null, null, null)
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        val updated = context(ctx) { tasks.byId(task.id).status(TaskStatus.COMPLETED) }
                        updated.save()
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }

            assertNotNull(taskCompletedAt(task.id))

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        val updated = context(ctx) { tasks.byId(task.id).status(TaskStatus.IN_PROGRESS) }
                        updated.save()
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            assertNull(taskCompletedAt(task.id))
        }

    // ─── attach() / detach() ─────────────────────────────────────────────────

    @Test
    fun `attach добавляет вложение`() =
        runTest {
            val upload = insertUpload()
            val task =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) {
                            tasks.new(TaskId.new(), "Задача", "", null, null, null)
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        val updated = context(ctx) { tasks.byId(task.id).attach(upload) }
                        updated.save()
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(1L, countAttachments(task.id))
        }

    @Test
    fun `attach идемпотентна повторный вызов не дублирует`() =
        runTest {
            val upload = insertUpload()
            val task =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) {
                            tasks.new(TaskId.new(), "Задача", "", null, null, null)
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        val withOne = context(ctx) { tasks.byId(task.id).attach(upload) }
                        val withSame = context(ctx) { withOne.attach(upload) }
                        withSame.save()
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(1L, countAttachments(task.id))
        }

    @Test
    fun `detach удаляет вложение`() =
        runTest {
            val upload1 = insertUpload()
            val upload2 = insertUpload()
            val task =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) {
                            tasks.new(TaskId.new(), "Задача", "", null, null, null)
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        val withAttachments =
                            context(ctx) {
                                tasks.byId(task.id).attach(upload1).attach(upload2)
                            }
                        withAttachments.save()
                    }
                }
            }.getOrElse { fail("Setup: $it") }

            assertEquals(2L, countAttachments(task.id))

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        val updated = context(ctx) { tasks.byId(task.id).detach(upload1) }
                        updated.save()
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(1L, countAttachments(task.id))

            val remaining =
                TestPostgres.db
                    .sql("SELECT upload_id::text FROM task_attachments WHERE task_id = :taskId")
                    .bind("taskId", task.id)
                    .list { row -> row.asString(0) }
                    .toSet()
            assertEquals(setOf(upload2.value.toString()), remaining)
        }

    // ─── byIds() ─────────────────────────────────────────────────────────────

    @Test
    fun `byIds возвращает несколько задач`() =
        runTest {
            val id1 = TaskId.new()
            val id2 = TaskId.new()
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        tasks.new(id1, "Задача 1", "", null, null, null)
                        tasks.new(id2, "Задача 2", "", null, null, null)
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val loaded =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { tasks.byIds(listOf(id1, id2)) }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(2, loaded.size)
            assertEquals(setOf(id1, id2), loaded.map { it.id }.toSet())
        }

    @Test
    fun `byIds возвращает ошибку если хотя бы один ID не найден`() =
        runTest {
            val id1 = TaskId.new()
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        tasks.new(id1, "Задача", "", null, null, null)
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { tasks.byIds(listOf(id1, TaskId.new())) }
                    }
                }

            assertIs<arrow.core.Either.Left<DomainError>>(result)
            assertEquals("TASK_NOT_FOUND", result.value.code)
        }

    // ─── list() / filters ─────────────────────────────────────────────────────

    @Test
    fun `list без фильтров возвращает все задачи организации`() =
        runTest {
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        tasks.new(TaskId.new(), "Задача 1", "", null, null, null)
                        tasks.new(TaskId.new(), "Задача 2", "", null, null, null)
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctxWithViewAll, this) {
                            tasks.list(TaskFilter(false, emptySet(), null, null, null, null, 50, 0))
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(2, result.items.size)
            assertEquals(2u, result.total)
        }

    @Test
    fun `list с onlyMine возвращает только свои задачи`() =
        runTest {
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        tasks.new(TaskId.new(), "Моя задача", "", null, null, null)
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(otherCtx, this) {
                        tasks.new(TaskId.new(), "Чужая задача", "", null, null, null)
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctxWithViewAll, this) {
                            tasks.list(TaskFilter(onlyMine = true, emptySet(), null, null, null, null, 50, 0))
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(1, result.items.size)
            assertEquals("Моя задача", result.items.first().title)
        }

    @Test
    fun `list без CAN_VIEW_ALL_TASKS видит только свои задачи`() =
        runTest {
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        tasks.new(TaskId.new(), "Моя задача", "", null, null, null)
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(otherCtx, this) {
                        tasks.new(TaskId.new(), "Чужая задача", "", null, null, null)
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) {
                            tasks.list(TaskFilter(false, emptySet(), null, null, null, null, 50, 0))
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(1, result.items.size)
            assertEquals("Моя задача", result.items.first().title)
        }

    @Test
    fun `list фильтрует по статусу`() =
        runTest {
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        tasks.new(TaskId.new(), "Ожидает", "", null, null, null)
                        val task2 = tasks.new(TaskId.new(), "В работе", "", null, null, null)
                        val updated = context(ctx) { task2.status(TaskStatus.IN_PROGRESS) }
                        updated.save()
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctxWithViewAll, this) {
                            tasks.list(TaskFilter(false, setOf(TaskStatus.PENDING), null, null, null, null, 50, 0))
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(1, result.items.size)
            assertEquals("Ожидает", result.items.first().title)
        }

    @Test
    fun `list фильтрует по searchText`() =
        runTest {
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        tasks.new(TaskId.new(), "Купить молоко", "", null, null, null)
                        tasks.new(TaskId.new(), "Позвонить клиенту", "", null, null, null)
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctxWithViewAll, this) {
                            tasks.list(TaskFilter(false, emptySet(), null, null, null, "молоко", 50, 0))
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(1, result.items.size)
            assertEquals("Купить молоко", result.items.first().title)
        }

    @Test
    fun `list total отражает общее количество без пагинации`() =
        runTest {
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctxWithViewAll, this) {
                        repeat(5) { tasks.new(TaskId.new(), "Задача $it", "", null, null, null) }
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctxWithViewAll, this) {
                            tasks.list(TaskFilter(false, emptySet(), null, null, null, null, 2, 0))
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(2, result.items.size)
            assertEquals(5u, result.total)
        }

    // ─── byId() ───────────────────────────────────────────────────────────────

    @Test
    fun `byId возвращает задачу с вложениями`() =
        runTest {
            val upload = insertUpload()
            val taskId = TaskId.new()

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        val task = tasks.new(taskId, "Задача", "", null, null, null)
                        val updated = context(ctx) { task.attach(upload) }
                        updated.save()
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val loaded =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { tasks.byId(taskId) }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            assertEquals(taskId, loaded.id)
            assertEquals(1, loaded.attachments.size)
            assertEquals(upload, loaded.attachments.first())
        }

    @Test
    fun `byId возвращает ошибку для несуществующей задачи`() =
        runTest {
            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { tasks.byId(TaskId.new()) }
                    }
                }

            assertIs<arrow.core.Either.Left<DomainError>>(result)
            assertEquals("TASK_NOT_FOUND", result.value.code)
        }

    // ─── проверка прав ────────────────────────────────────────────────────────

    @Test
    fun `withNew разрешает редактировать собственную задачу`() =
        runTest {
            val task =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) {
                            tasks.new(TaskId.new(), "Оригинал", "", null, null, null)
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        val updated = context(ctx) { tasks.byId(task.id).withNew("Изменено", "", null, null, null) }
                        updated.save()
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val loaded =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { tasks.byId(task.id) }
                    }
                }.getOrElse { fail("Unexpected error: $it") }
            assertEquals("Изменено", loaded.title)
        }

    @Test
    fun `withNew отклоняет редактирование чужой задачи без прав`() =
        runTest {
            val task =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(otherCtx, this) {
                            tasks.new(TaskId.new(), "Чужая", "", null, null, null)
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            val result =
                either<DomainError, Unit> {
                    TestPostgres.db.transaction {
                        context(ctx, this) {
                            val updated = context(ctx) { tasks.byId(task.id).withNew("Попытка", "", null, null, null) }
                            updated.save()
                        }
                    }
                }

            assertIs<arrow.core.Either.Left<DomainError>>(result)
            assertEquals("PERMISSION_DENIED", result.value.code)
        }

    @Test
    fun `withNew разрешает редактировать чужую задачу с CAN_MANAGE_TASKS`() =
        runTest {
            val task =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(otherCtx, this) {
                            tasks.new(TaskId.new(), "Чужая", "", null, null, null)
                        }
                    }
                }.getOrElse { fail("Unexpected error: $it") }

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctxManage, this) {
                        val updated = context(ctxManage) { tasks.byId(task.id).withNew("Менеджер изменил", "", null, null, null) }
                        updated.save()
                    }
                }
            }.getOrElse { fail("Unexpected error: $it") }

            val loaded =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctxManage, this) { tasks.byId(task.id) }
                    }
                }.getOrElse { fail("Unexpected error: $it") }
            assertEquals("Менеджер изменил", loaded.title)
        }
}
