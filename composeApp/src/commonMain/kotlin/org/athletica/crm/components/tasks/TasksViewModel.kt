package org.athletica.crm.components.tasks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.tasks.TaskListItemSchema
import org.athletica.crm.api.schemas.tasks.TaskListRequest
import org.athletica.crm.core.tasks.TaskStatus

/** Состояние загрузки списка задач. */
sealed class TasksState {
    /** Загрузка в процессе. */
    data object Loading : TasksState()

    /** Список загружен. */
    data class Loaded(
        val tasks: List<TaskListItemSchema>,
        val total: UInt,
    ) : TasksState()

    /** Ошибка загрузки. */
    data object Error : TasksState()
}

/** Активные фильтры списка задач. */
data class TasksFilters(
    val onlyMine: Boolean = false,
    val statuses: Set<TaskStatus> = emptySet(),
    val searchText: String = "",
)

/**
 * ViewModel экрана списка задач.
 * Управляет загрузкой, фильтрацией и пагинацией задач.
 */
class TasksViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
) {
    var state by mutableStateOf<TasksState>(TasksState.Loading)
        private set

    /** Текущие фильтры. Изменение фильтров автоматически перезагружает список. */
    var filters by mutableStateOf(TasksFilters())
        private set

    /** Загружает список задач с текущими фильтрами. */
    fun load() {
        scope.launch {
            state = TasksState.Loading
            val result =
                api.tasks.list(
                    TaskListRequest(
                        onlyMine = filters.onlyMine,
                        statuses = filters.statuses.toList(),
                        searchText = filters.searchText.takeIf { it.isNotBlank() },
                    ),
                )
            state =
                result.fold(
                    ifLeft = { TasksState.Error },
                    ifRight = { TasksState.Loaded(it.tasks, it.total) },
                )
        }
    }

    /** Обновляет фильтры и перезагружает список. */
    fun updateFilters(newFilters: TasksFilters) {
        filters = newFilters
        load()
    }
}
