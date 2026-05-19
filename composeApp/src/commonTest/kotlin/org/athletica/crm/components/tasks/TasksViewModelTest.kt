package org.athletica.crm.components.tasks

import arrow.core.Either
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.tasks.TaskListItemSchema
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.core.tasks.TaskStatus
import org.athletica.crm.ui.list.ListPageViewModel
import org.athletica.crm.ui.list.ListState
import org.athletica.crm.ui.list.SortDirection
import org.athletica.crm.ui.list.SortState
import org.athletica.crm.ui.list.SystemSavedView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** Тесты бизнес-логики раздела «Задачи» через тестовый подкласс [ListPageViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    private fun makeTask(
        title: String,
        status: TaskStatus = TaskStatus.PENDING,
        dueDate: kotlin.time.Instant? = null,
        assigneeName: String? = null,
    ) = TaskListItemSchema(
        id = TaskId(Uuid.random()),
        title = title,
        assigneeId = null,
        assigneeName = assigneeName,
        clientId = null,
        clientName = null,
        status = status,
        dueDate = dueDate,
        dueDateEnd = null,
    )

    /**
     * Тестовая ViewModel с теми же реализациями [matches] и [compare], что и [TasksViewModel],
     * но без зависимостей от API и DisplaySettingsViewModel.
     */
    private inner class StubTasksVm(
        items: List<TaskListItemSchema> = emptyList(),
    ) : ListPageViewModel<TaskListItemSchema, TasksFilter>(scope) {
        init {
            state = ListState.Loaded(items)
        }

        override fun defaultFilter(): TasksFilter = TasksFilter()

        override fun defaultSort(): SortState? = null

        override suspend fun fetch(): Either<ApiClientError, List<TaskListItemSchema>> = Either.Right((state as? ListState.Loaded)?.items ?: emptyList())

        override fun matches(
            item: TaskListItemSchema,
            query: String,
            filter: TasksFilter,
        ): Boolean = true

        override fun compare(
            a: TaskListItemSchema,
            b: TaskListItemSchema,
            sort: SortState,
        ): Int {
            val cmp =
                when (sort.columnId) {
                    TasksColumns.Title -> a.title.compareTo(b.title, ignoreCase = true)
                    TasksColumns.Status -> a.status.ordinal - b.status.ordinal
                    TasksColumns.Assignee ->
                        (a.assigneeName ?: "").compareTo(b.assigneeName ?: "", ignoreCase = true)
                    TasksColumns.DueDate -> compareValues(a.dueDate, b.dueDate)
                    else -> 0
                }
            return if (sort.direction == SortDirection.Asc) cmp else -cmp
        }
    }

    // ── Тест 1: TasksFilter.activeCount ─────────────────────────────────────

    @Test
    fun `TasksFilter activeCount корректно считает активные фильтры`() {
        assertEquals(0, TasksFilter().activeCount)
        assertEquals(1, TasksFilter(onlyMine = true).activeCount)
        assertEquals(1, TasksFilter(statuses = setOf(TaskStatus.PENDING)).activeCount)
        assertEquals(2, TasksFilter(onlyMine = true, statuses = setOf(TaskStatus.PENDING)).activeCount)
    }

    // ── Тест 2: applySystemView устанавливает фильтр, сортировку и activeSavedViewId ──

    @Test
    fun `applySystemView MY_TASKS устанавливает фильтр сортировку и activeSavedViewId`() {
        val vm = StubTasksVm()
        vm.applySystemView(TasksSavedViews.MY_TASKS)

        assertEquals(TasksFilter(onlyMine = true), vm.filter)
        assertEquals(TasksSavedViews.MY_TASKS.sort, vm.sort)
        assertEquals(TasksSavedViews.MY_TASKS.id, vm.activeSavedViewId)
    }

    // ── Тест 3: сортировка по dueDate — null-first при Asc ───────────────────

    @Test
    fun `сортировка по dueDate — задача без даты идёт первой при Asc null-first`() {
        val withDate =
            makeTask("A").copy(dueDate = kotlin.time.Instant.fromEpochMilliseconds(1_000_000))
        val withoutDate = makeTask("B")
        val vm = StubTasksVm(listOf(withDate, withoutDate))

        vm.cycleSort(TasksColumns.DueDate)
        assertEquals(SortDirection.Asc, vm.sort?.direction)
        assertEquals(withoutDate.id, vm.visible.first().id, "null-dueDate идёт первым при Asc")

        vm.cycleSort(TasksColumns.DueDate)
        assertEquals(SortDirection.Desc, vm.sort?.direction)
        assertEquals(withDate.id, vm.visible.first().id, "задача с датой идёт первой при Desc")
    }

    // ── Тест 4: сортировка по title без учёта регистра ───────────────────────

    @Test
    fun `сортировка по title без учёта регистра при Asc`() {
        val taskA = makeTask("alpha")
        val taskB = makeTask("Beta")
        val vm = StubTasksVm(listOf(taskB, taskA))

        vm.cycleSort(TasksColumns.Title)
        assertEquals(listOf("alpha", "Beta"), vm.visible.map { it.title })
    }

    // ── Тест 5: cycleSort циклически переключает сортировку ──────────────────

    @Test
    fun `cycleSort переключает none → Asc → Desc → none`() =
        runTest(dispatcher) {
            val vm =
                StubTasksVm(
                    listOf(
                        makeTask("C"),
                        makeTask("A"),
                        makeTask("B"),
                    ),
                )

            assertNull(vm.sort)

            vm.cycleSort(TasksColumns.Title)
            assertEquals(SortState(TasksColumns.Title, SortDirection.Asc), vm.sort)
            assertEquals(listOf("A", "B", "C"), vm.visible.map { it.title })

            vm.cycleSort(TasksColumns.Title)
            assertEquals(SortState(TasksColumns.Title, SortDirection.Desc), vm.sort)
            assertEquals(listOf("C", "B", "A"), vm.visible.map { it.title })

            vm.cycleSort(TasksColumns.Title)
            assertNull(vm.sort)
        }

    // ── Тест 6: applySort прямо устанавливает сортировку ────────────────────

    @Test
    fun `applySort устанавливает сортировку и сбрасывает activeSavedViewId`() {
        val vm = StubTasksVm()
        vm.applySystemView(
            SystemSavedView(
                id = TasksSavedViews.ALL.id,
                filter = TasksFilter(),
                sort = null,
            ),
        )
        assertEquals(TasksSavedViews.ALL.id, vm.activeSavedViewId)

        vm.applySort(SortState(TasksColumns.Status, SortDirection.Desc))

        assertEquals(SortState(TasksColumns.Status, SortDirection.Desc), vm.sort)
        assertNull(vm.activeSavedViewId)
    }

    // ── Тест 7: TasksSavedViews.ALL_VIEWS содержит оба вида ─────────────────

    @Test
    fun `TasksSavedViews ALL_VIEWS содержит ALL и MY_TASKS`() {
        assertEquals(2, TasksSavedViews.ALL_VIEWS.size)
        assertIs<SystemSavedView<TasksFilter>>(TasksSavedViews.ALL_VIEWS[0])
        assertEquals(TasksSavedViews.ALL.id, TasksSavedViews.ALL_VIEWS[0].id)
        assertEquals(TasksSavedViews.MY_TASKS.id, TasksSavedViews.ALL_VIEWS[1].id)
    }
}
