package org.athletica.crm.components.tasks

import arrow.core.Either
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.tasks.TaskListItemSchema
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.core.tasks.TaskStatus
import org.athletica.crm.ui.list.FetchResult
import org.athletica.crm.ui.list.ListPageDelegate
import org.athletica.crm.ui.list.ListPageViewModel
import org.athletica.crm.ui.list.SortDirection
import org.athletica.crm.ui.list.SortState
import org.athletica.crm.ui.list.SystemSavedView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/**
 * Тесты бизнес-логики раздела «Задачи» через [TasksPageDelegate] + [ListPageViewModel].
 * Используется тестовый делегат с теми же реализациями [ListPageDelegate.matches]
 * и [ListPageDelegate.compare], что и [TasksPageDelegate], но без зависимостей
 * от API и DisplaySettingsViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TasksPageDelegateTest {
    /** Фабрика тестовой задачи с минимальными полями. */
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

    /** Тестовый делегат раздела «Задачи» с детерминированным fetch. */
    private class StubTasksDelegate(
        val items: List<TaskListItemSchema> = emptyList(),
    ) : ListPageDelegate<TaskListItemSchema, TasksFilter> {
        override fun defaultFilter(): TasksFilter = TasksFilter()

        override fun defaultSort(): SortState? = null

        override suspend fun fetch(
            filter: TasksFilter,
            searchQuery: String,
        ): Either<ApiClientError, FetchResult<TaskListItemSchema>> = Either.Right(FetchResult(items))

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

    @Test
    fun `TasksFilter activeCount корректно считает активные фильтры`() {
        assertEquals(0, TasksFilter().activeCount)
        assertEquals(1, TasksFilter(onlyMine = true).activeCount)
        assertEquals(1, TasksFilter(statuses = setOf(TaskStatus.PENDING)).activeCount)
        assertEquals(2, TasksFilter(onlyMine = true, statuses = setOf(TaskStatus.PENDING)).activeCount)
    }

    @Test
    fun `applySystemView MY_TASKS устанавливает фильтр сортировку и activeSavedViewId`() =
        runTest(UnconfinedTestDispatcher()) {
            val vm = ListPageViewModel(StubTasksDelegate(), backgroundScope)
            vm.applySystemView(TasksSavedViews.MY_TASKS)

            assertEquals(TasksFilter(onlyMine = true), vm.state.filter)
            assertEquals(TasksSavedViews.MY_TASKS.sort, vm.state.sort)
            assertEquals(TasksSavedViews.MY_TASKS.id, vm.state.activeSavedViewId)
        }

    @Test
    fun `сортировка по dueDate — задача без даты идёт первой при Asc null-first`() =
        runTest(UnconfinedTestDispatcher()) {
            val withDate =
                makeTask("A").copy(dueDate = kotlin.time.Instant.fromEpochMilliseconds(1_000_000))
            val withoutDate = makeTask("B")
            val vm =
                ListPageViewModel(
                    StubTasksDelegate(listOf(withDate, withoutDate)),
                    backgroundScope,
                )
            vm.load()

            vm.cycleSort(TasksColumns.DueDate)
            assertEquals(SortDirection.Asc, vm.state.sort?.direction)
            assertEquals(withoutDate.id, vm.visible.first().id, "null-dueDate идёт первым при Asc")

            vm.cycleSort(TasksColumns.DueDate)
            assertEquals(SortDirection.Desc, vm.state.sort?.direction)
            assertEquals(withDate.id, vm.visible.first().id, "задача с датой идёт первой при Desc")
        }

    @Test
    fun `сортировка по title без учёта регистра при Asc`() =
        runTest(UnconfinedTestDispatcher()) {
            val taskA = makeTask("alpha")
            val taskB = makeTask("Beta")
            val vm =
                ListPageViewModel(StubTasksDelegate(listOf(taskB, taskA)), backgroundScope)
            vm.load()

            vm.cycleSort(TasksColumns.Title)
            assertEquals(listOf("alpha", "Beta"), vm.visible.map { it.title })
        }

    @Test
    fun `cycleSort переключает none → Asc → Desc → none`() =
        runTest(UnconfinedTestDispatcher()) {
            val vm =
                ListPageViewModel(
                    StubTasksDelegate(
                        listOf(
                            makeTask("C"),
                            makeTask("A"),
                            makeTask("B"),
                        ),
                    ),
                    backgroundScope,
                )
            vm.load()

            assertNull(vm.state.sort)

            vm.cycleSort(TasksColumns.Title)
            assertEquals(SortState(TasksColumns.Title, SortDirection.Asc), vm.state.sort)
            assertEquals(listOf("A", "B", "C"), vm.visible.map { it.title })

            vm.cycleSort(TasksColumns.Title)
            assertEquals(SortState(TasksColumns.Title, SortDirection.Desc), vm.state.sort)
            assertEquals(listOf("C", "B", "A"), vm.visible.map { it.title })

            vm.cycleSort(TasksColumns.Title)
            assertNull(vm.state.sort)
        }

    @Test
    fun `applySort устанавливает сортировку и сбрасывает activeSavedViewId`() =
        runTest(UnconfinedTestDispatcher()) {
            val vm = ListPageViewModel(StubTasksDelegate(), backgroundScope)
            vm.applySystemView(
                SystemSavedView(
                    id = TasksSavedViews.ALL.id,
                    filter = TasksFilter(),
                    sort = null,
                ),
            )
            assertEquals(TasksSavedViews.ALL.id, vm.state.activeSavedViewId)

            vm.applySort(SortState(TasksColumns.Status, SortDirection.Desc))

            assertEquals(SortState(TasksColumns.Status, SortDirection.Desc), vm.state.sort)
            assertNull(vm.state.activeSavedViewId)
        }

    @Test
    fun `TasksSavedViews ALL_VIEWS содержит ALL и MY_TASKS`() {
        assertEquals(2, TasksSavedViews.ALL_VIEWS.size)
        assertIs<SystemSavedView<TasksFilter>>(TasksSavedViews.ALL_VIEWS[0])
        assertEquals(TasksSavedViews.ALL.id, TasksSavedViews.ALL_VIEWS[0].id)
        assertEquals(TasksSavedViews.MY_TASKS.id, TasksSavedViews.ALL_VIEWS[1].id)
    }
}
