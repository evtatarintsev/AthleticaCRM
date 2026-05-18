package org.athletica.crm.components.tasks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.tasks.TaskDetailRequest
import org.athletica.crm.api.schemas.tasks.TaskDetailResponse
import org.athletica.crm.api.schemas.tasks.UpdateTaskRequest
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.core.tasks.TaskStatus

/** Состояние экрана детального просмотра задачи. */
sealed class TaskDetailState {
    /** Загрузка данных. */
    data object Loading : TaskDetailState()

    /** Данные загружены. */
    data class Loaded(val task: TaskDetailResponse) : TaskDetailState()

    /** Ошибка загрузки. */
    data object Error : TaskDetailState()
}

/**
 * ViewModel экрана детального просмотра задачи.
 * Позволяет просматривать и изменять статус, а также редактировать задачу.
 */
class TaskDetailViewModel(
    private val taskId: TaskId,
    private val api: ApiClient,
    private val scope: CoroutineScope,
) {
    var state by mutableStateOf<TaskDetailState>(TaskDetailState.Loading)
        private set

    /** Загружает данные задачи. */
    fun load() {
        scope.launch {
            state = TaskDetailState.Loading
            val result = api.tasks.detail(TaskDetailRequest(taskId))
            state =
                result.fold(
                    ifLeft = { TaskDetailState.Error },
                    ifRight = { TaskDetailState.Loaded(it) },
                )
        }
    }

    /** Меняет статус задачи без изменения остальных полей. */
    fun changeStatus(newStatus: TaskStatus) {
        val loaded = state as? TaskDetailState.Loaded ?: return
        val task = loaded.task
        scope.launch {
            val result =
                api.tasks.update(
                    UpdateTaskRequest(
                        id = task.id,
                        title = task.title,
                        description = task.description,
                        assigneeId = task.assigneeId,
                        clientId = task.clientId,
                        status = newStatus,
                        dueDate = task.dueDate,
                        dueDateEnd = task.dueDateEnd,
                        attachments = task.attachments.map { it.id },
                    ),
                )
            result.onRight { state = TaskDetailState.Loaded(it) }
        }
    }
}
