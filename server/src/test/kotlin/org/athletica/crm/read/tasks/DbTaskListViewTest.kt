package org.athletica.crm.read.tasks

import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.tasks.TaskListResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.core.tasks.TaskStatus
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.domain.tasks.DbTasks
import org.athletica.crm.storage.Transaction
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

/** Тесты read-проекции списка задач: видимость, фильтры, пагинация и подстановка имён. */
class DbTaskListViewTest {
    private val orgId = OrgId.new()
    private val userId = UserId.new()
    private val employeeId = EmployeeId.new()
    private val otherEmployeeId = EmployeeId.new()
    private val branchId = BranchId.new()

    private val tasks = DbTasks()
    private val view = DbTaskListView()

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

    private suspend fun insertClient(name: String): ClientId {
        val clientId = ClientId.new()
        TestPostgres.db
            .sql("INSERT INTO clients (id, org_id, name, gender) VALUES (:id, :orgId, :name, 'MALE'::gender)")
            .bind("id", clientId).bind("orgId", orgId).bind("name", name)
            .execute()
        return clientId
    }

    /** Назначает исполнителя напрямую в БД: проекции агрегат `Employee` не требуется. */
    private suspend fun assignTo(taskId: TaskId, assigneeId: EmployeeId) {
        TestPostgres.db
            .sql("UPDATE tasks SET assignee_id = :assigneeId WHERE id = :id")
            .bind("assigneeId", assigneeId)
            .bind("id", taskId)
            .execute()
    }

    /** Выполняет [block] в транзакции под контекстом [context], разворачивая ошибку в падение теста. */
    private suspend fun <T> inContext(
        context: EmployeeRequestContext,
        block: suspend context(EmployeeRequestContext, Transaction, Raise<DomainError>) () -> T,
    ): T =
        either {
            val raise: Raise<DomainError> = this
            TestPostgres.db.transaction {
                context(context, this, raise) { block() }
            }
        }.getOrElse { fail("Unexpected error: $it") }

    private suspend fun page(
        context: EmployeeRequestContext = ctxWithViewAll,
        query: TaskListQuery = query(),
    ): TaskListResponse = inContext(context) { view.page(query) }

    private fun query(
        onlyMine: Boolean = false,
        statuses: Set<TaskStatus> = emptySet(),
        clientId: ClientId? = null,
        searchText: String? = null,
        limit: Int = 50,
        offset: Int = 0,
    ) = TaskListQuery(
        onlyMine = onlyMine,
        statuses = statuses,
        dueDateFrom = null,
        dueDateTo = null,
        clientId = clientId,
        searchText = searchText,
        limit = limit,
        offset = offset,
    )

    @Test
    fun `без фильтров возвращает все задачи организации`() =
        runTest {
            inContext(ctx) {
                tasks.new(TaskId.new(), "Задача 1", "", null, null, null)
                tasks.new(TaskId.new(), "Задача 2", "", null, null, null)
            }

            val result = page()

            assertEquals(2, result.tasks.size)
            assertEquals(2u, result.total)
        }

    @Test
    fun `onlyMine возвращает только свои задачи`() =
        runTest {
            inContext(ctx) { tasks.new(TaskId.new(), "Моя задача", "", null, null, null) }
            inContext(otherCtx) { tasks.new(TaskId.new(), "Чужая задача", "", null, null, null) }

            val result = page(query = query(onlyMine = true))

            assertEquals(1, result.tasks.size)
            assertEquals("Моя задача", result.tasks.first().title)
        }

    @Test
    fun `без CAN_VIEW_ALL_TASKS видно только свои задачи`() =
        runTest {
            inContext(ctx) { tasks.new(TaskId.new(), "Моя задача", "", null, null, null) }
            inContext(otherCtx) { tasks.new(TaskId.new(), "Чужая задача", "", null, null, null) }

            val result = page(context = ctx)

            assertEquals(1, result.tasks.size)
            assertEquals("Моя задача", result.tasks.first().title)
        }

    @Test
    fun `фильтр по статусу`() =
        runTest {
            inContext(ctx) {
                tasks.new(TaskId.new(), "Ожидает", "", null, null, null)
                val inProgress = tasks.new(TaskId.new(), "В работе", "", null, null, null)
                inProgress.status(TaskStatus.IN_PROGRESS).save()
            }

            val result = page(query = query(statuses = setOf(TaskStatus.PENDING)))

            assertEquals(1, result.tasks.size)
            assertEquals("Ожидает", result.tasks.first().title)
        }

    @Test
    fun `фильтр по searchText`() =
        runTest {
            inContext(ctx) {
                tasks.new(TaskId.new(), "Купить молоко", "", null, null, null)
                tasks.new(TaskId.new(), "Позвонить клиенту", "", null, null, null)
            }

            val result = page(query = query(searchText = "молоко"))

            assertEquals(1, result.tasks.size)
            assertEquals("Купить молоко", result.tasks.first().title)
        }

    @Test
    fun `total отражает общее количество без пагинации`() =
        runTest {
            inContext(ctx) { repeat(5) { tasks.new(TaskId.new(), "Задача $it", "", null, null, null) } }

            val result = page(query = query(limit = 2))

            assertEquals(2, result.tasks.size)
            assertEquals(5u, result.total)
        }

    @Test
    fun `подставляет имена исполнителя и клиента join'ом`() =
        runTest {
            val clientId = insertClient("Анна Клиентова")
            val taskId = TaskId.new()
            inContext(ctx) { tasks.new(taskId, "С исполнителем", "", clientId, null, null) }
            assignTo(taskId, otherEmployeeId)

            val item = page().tasks.single()

            assertEquals(otherEmployeeId, item.assigneeId)
            assertEquals("Пётр", item.assigneeName)
            assertEquals(clientId, item.clientId)
            assertEquals("Анна Клиентова", item.clientName)
        }

    @Test
    fun `без исполнителя и клиента имена пустые`() =
        runTest {
            inContext(ctx) { tasks.new(TaskId.new(), "Одинокая задача", "", null, null, null) }

            val item = page().tasks.single()

            assertNull(item.assigneeId)
            assertNull(item.assigneeName)
            assertNull(item.clientId)
            assertNull(item.clientName)
        }
}
