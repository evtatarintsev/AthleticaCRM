package org.athletica.crm.domain.tasks

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.SystemRequestContext
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.core.tasks.TaskStatus
import org.athletica.crm.domain.EmployeeStub
import org.athletica.crm.domain.employees.EmployeeBranchAccess
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.employeeContext
import org.athletica.crm.read.tasks.DbTaskDetailView
import org.athletica.crm.read.tasks.DbTaskListView
import org.athletica.crm.read.tasks.TaskListQuery
import org.athletica.crm.storage.Transaction
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.fail

/** Тесты задач, созданных системой: автор, видимость без исполнителя, «взять себе». */
class SystemTasksTest {
    private val orgId = OrgId.new()
    private val employeeId = EmployeeId.new()
    private val otherEmployeeId = EmployeeId.new()
    private val ctx = employeeContext(orgId = orgId, employeeId = employeeId, employeeName = "Иван")
    private val otherCtx = employeeContext(orgId = orgId, employeeId = otherEmployeeId, employeeName = "Пётр")
    private val system = SystemRequestContext(orgId = orgId, currency = Currency.RUB)
    private val tasks = DbTasks()

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, 'Org')").bind("id", orgId).execute()
            listOf(employeeId to "Иван", otherEmployeeId to "Пётр").forEach { (id, name) ->
                TestPostgres.db
                    .sql("INSERT INTO employees (id, org_id, name) VALUES (:id, :orgId, :name)")
                    .bind("id", id)
                    .bind("orgId", orgId)
                    .bind("name", name)
                    .execute()
            }
        }
    }

    private suspend fun <T> attempt(context: RequestContext, block: suspend context(RequestContext, Transaction, Raise<DomainError>) () -> T): Either<DomainError, T> =
        either {
            val raise: Raise<DomainError> = this
            TestPostgres.db.transaction { block(context, this, raise) }
        }

    private suspend fun <T> tx(context: RequestContext, block: suspend context(RequestContext, Transaction, Raise<DomainError>) () -> T): T = attempt(context, block).getOrElse { fail("Unexpected error: $it") }

    private suspend fun systemTask(): TaskId {
        val id = TaskId.new()
        tx(system) { tasks.new(id, "Проверить клиента", "", null, null, null) }
        return id
    }

    private fun stub(id: EmployeeId) = EmployeeStub(id = id, userId = null, name = "Stub", permissionSettings = EmployeePermission(), availableBranches = EmployeeBranchAccess.All)

    @Test
    fun `создание задачи в системном контексте и карточка системной задачи`() =
        runTest {
            val id = systemTask()

            val row = tx(ctx) { context(ctx) { DbTaskDetailView().byId(id) } }

            assertNull(row.createdBy)
            assertNull(row.createdByName)
        }

    @Test
    fun `задача сотрудника не становится системной`() =
        runTest {
            val id = TaskId.new()
            tx(ctx) { context(ctx) { tasks.new(id, "Позвонить", "", null, null, null) } }

            val row = tx(ctx) { context(ctx) { DbTaskDetailView().byId(id) } }

            assertEquals(employeeId, row.createdBy)
            assertEquals("Иван", row.createdByName)
        }

    @Test
    fun `системная задача видна сотруднику без права, но не в «только мои»`() =
        runTest {
            systemTask()
            val query = TaskListQuery(onlyMine = false, statuses = emptySet(), dueDateFrom = null, dueDateTo = null, clientId = null, searchText = null, limit = 50, offset = 0)

            val all = tx(ctx) { context(ctx) { DbTaskListView().page(query) } }
            val mine = tx(ctx) { context(ctx) { DbTaskListView().page(query.copy(onlyMine = true)) } }

            assertEquals(listOf("Проверить клиента"), all.tasks.map { it.title })
            assertEquals(emptyList(), mine.tasks)
        }

    @Test
    fun `сотрудник берёт системную задачу и затем редактирует её`() =
        runTest {
            val id = systemTask()

            tx(ctx) { context(ctx) { tasks.byId(id).assignTo(stub(employeeId)).save() } }
            tx(ctx) { context(ctx) { tasks.byId(id).status(TaskStatus.COMPLETED).save() } }

            val task = tx(ctx) { context(ctx) { tasks.byId(id) } }
            assertEquals(employeeId, task.assigneeId)
            assertNull(task.createdBy)
            assertEquals(TaskStatus.COMPLETED, task.status)
        }

    @Test
    fun `назначить другого на неназначенную задачу нельзя`() =
        runTest {
            val id = systemTask()

            val result = attempt(ctx) { context(ctx) { tasks.byId(id).assignTo(stub(otherEmployeeId)).save() } }

            assertEquals("PERMISSION_DENIED", assertIs<Either.Left<DomainError>>(result).value.code)
        }

    @Test
    fun `сменить статус без взятия нельзя`() =
        runTest {
            val id = systemTask()

            val result = attempt(otherCtx) { context(otherCtx) { tasks.byId(id).status(TaskStatus.IN_PROGRESS).save() } }

            assertEquals("PERMISSION_DENIED", assertIs<Either.Left<DomainError>>(result).value.code)
        }
}
