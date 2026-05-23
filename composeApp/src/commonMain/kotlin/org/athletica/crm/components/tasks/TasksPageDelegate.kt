package org.athletica.crm.components.tasks

import arrow.core.Either
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.tasks.TaskListItemSchema
import org.athletica.crm.api.schemas.tasks.TaskListRequest
import org.athletica.crm.components.settings.DisplaySettingsViewModel
import org.athletica.crm.core.tasks.TaskStatus
import org.athletica.crm.ui.list.ColumnId
import org.athletica.crm.ui.list.FetchResult
import org.athletica.crm.ui.list.ListPageDelegate
import org.athletica.crm.ui.list.SavedViewId
import org.athletica.crm.ui.list.SortDirection
import org.athletica.crm.ui.list.SortState
import org.athletica.crm.ui.list.SystemSavedView

/**
 * Типизированный фильтр раздела «Задачи».
 * [onlyMine] — показывать только задачи текущего сотрудника.
 * [statuses] — множество выбранных статусов; пустое — все статусы.
 */
data class TasksFilter(
    val onlyMine: Boolean = false,
    val statuses: Set<TaskStatus> = emptySet(),
) {
    /** Количество активных фильтров для бейджа кнопки «Фильтры». */
    val activeCount: Int = (if (onlyMine) 1 else 0) + (if (statuses.isNotEmpty()) 1 else 0)
}

/**
 * Делегат страницы списка задач для [org.athletica.crm.ui.list.ListPageViewModel].
 *
 * Полностью без собственного состояния и корутин: предоставляет загрузку
 * через API, клиентскую фильтрацию/сортировку и хук персиста сортировки
 * в [displaySettingsVm].
 */
class TasksPageDelegate(
    private val api: ApiClient,
    private val displaySettingsVm: DisplaySettingsViewModel,
) : ListPageDelegate<TaskListItemSchema, TasksFilter> {
    override fun defaultFilter(): TasksFilter = TasksFilter()

    override fun defaultSort(): SortState? = displaySettingsVm.displaySettings.tasks.sort?.let(SortState::fromSchema)

    override suspend fun fetch(
        filter: TasksFilter,
        searchQuery: String,
    ): Either<ApiClientError, FetchResult<TaskListItemSchema>> =
        api.tasks
            .list(
                TaskListRequest(
                    onlyMine = filter.onlyMine,
                    statuses = filter.statuses.toList(),
                    searchText = searchQuery.takeIf { it.isNotBlank() },
                ),
            ).map { response ->
                FetchResult(items = response.tasks, total = response.total.toInt())
            }

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

    override fun onSortChanged(sort: SortState?) {
        val current = displaySettingsVm.displaySettings
        displaySettingsVm.update(
            current.copy(
                tasks = current.tasks.copy(sort = sort?.toSchema()),
            ),
        )
    }
}

/** Идентификаторы колонок таблицы задач. */
object TasksColumns {
    val Title = ColumnId("title")
    val Status = ColumnId("status")
    val Assignee = ColumnId("assignee")
    val DueDate = ColumnId("dueDate")
}

/** Системные сохранённые виды раздела «Задачи». */
object TasksSavedViews {
    val ALL: SystemSavedView<TasksFilter> =
        SystemSavedView(
            id = SavedViewId.system("tasks:all"),
            filter = TasksFilter(),
        )

    val MY_TASKS: SystemSavedView<TasksFilter> =
        SystemSavedView(
            id = SavedViewId.system("tasks:my"),
            filter = TasksFilter(onlyMine = true),
            sort = SortState(TasksColumns.DueDate, SortDirection.Asc),
        )

    /** Полный список системных видов в порядке отображения. */
    val ALL_VIEWS: List<SystemSavedView<TasksFilter>> = listOf(ALL, MY_TASKS)
}
