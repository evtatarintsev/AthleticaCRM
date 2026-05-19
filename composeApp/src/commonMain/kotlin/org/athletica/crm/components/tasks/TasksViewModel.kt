package org.athletica.crm.components.tasks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import arrow.core.Either
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.settings.DisplaySettings
import org.athletica.crm.api.schemas.tasks.TaskListItemSchema
import org.athletica.crm.api.schemas.tasks.TaskListRequest
import org.athletica.crm.components.settings.DisplaySettingsViewModel
import org.athletica.crm.core.tasks.TaskStatus
import org.athletica.crm.ui.list.ListPageViewModel
import org.athletica.crm.ui.list.SavedViewId
import org.athletica.crm.ui.list.SortDirection
import org.athletica.crm.ui.list.SortState
import org.athletica.crm.ui.list.SystemSavedView
import kotlin.time.Duration.Companion.milliseconds

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
 * ViewModel экрана списка задач.
 * Расширяет [ListPageViewModel] с серверной фильтрацией.
 * Сохраняет сортировку в [DisplaySettings.tasks].
 */
@OptIn(FlowPreview::class)
class TasksViewModel(
    private val api: ApiClient,
    private val displaySettingsVm: DisplaySettingsViewModel,
    scope: CoroutineScope,
) : ListPageViewModel<TaskListItemSchema, TasksFilter>(scope) {
    /** Общее число задач, удовлетворяющих фильтру (без пагинации). */
    var total: UInt by mutableStateOf(0u)
        private set

    init {
        scope.launch {
            snapshotFlow { filter }
                .drop(1)
                .collectLatest { load() }
        }
        scope.launch {
            snapshotFlow { searchQuery }
                .drop(1)
                .debounce(300.milliseconds)
                .collectLatest { load() }
        }
        scope.launch {
            snapshotFlow { sort }
                .drop(1)
                .collectLatest { newSort -> persistSort(newSort) }
        }
    }

    override fun defaultFilter(): TasksFilter = TasksFilter()

    override fun defaultSort(): SortState? = displaySettingsVm.displaySettings.tasks.sort?.let { SortState.fromDto(it) }

    override suspend fun fetch(): Either<ApiClientError, List<TaskListItemSchema>> =
        api.tasks
            .list(
                TaskListRequest(
                    onlyMine = filter.onlyMine,
                    statuses = filter.statuses.toList(),
                    searchText = searchQuery.takeIf { it.isNotBlank() },
                ),
            ).map { response ->
                total = response.total
                response.tasks
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

    private fun persistSort(newSort: SortState?) {
        val current = displaySettingsVm.displaySettings
        displaySettingsVm.update(
            current.copy(
                tasks = current.tasks.copy(sort = newSort?.toDto()),
            ),
        )
    }
}

/** Идентификаторы колонок таблицы задач. */
object TasksColumns {
    val Title = org.athletica.crm.ui.list.ColumnId("title")
    val Status = org.athletica.crm.ui.list.ColumnId("status")
    val Assignee = org.athletica.crm.ui.list.ColumnId("assignee")
    val DueDate = org.athletica.crm.ui.list.ColumnId("dueDate")
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
