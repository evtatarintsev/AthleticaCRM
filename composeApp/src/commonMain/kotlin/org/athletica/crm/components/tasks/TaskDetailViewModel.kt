package org.athletica.crm.components.tasks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.tasks.AssignTaskRequest
import org.athletica.crm.api.schemas.tasks.AttachTaskUploadRequest
import org.athletica.crm.api.schemas.tasks.DetachTaskUploadRequest
import org.athletica.crm.api.schemas.tasks.TaskDetailRequest
import org.athletica.crm.api.schemas.tasks.TaskDetailResponse
import org.athletica.crm.api.schemas.tasks.UnassignTaskRequest
import org.athletica.crm.api.schemas.tasks.UpdateTaskRequest
import org.athletica.crm.api.schemas.tasks.UpdateTaskStatusRequest
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.core.tasks.TaskStatus
import org.athletica.crm.pickAnyFile
import kotlin.time.Instant

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
 * Позволяет просматривать и изменять статус задачи.
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

    /** Меняет статус задачи. */
    fun changeStatus(newStatus: TaskStatus) {
        scope.launch {
            val result =
                api.tasks.updateStatus(
                    UpdateTaskStatusRequest(
                        taskIds = listOf(taskId),
                        status = newStatus,
                    ),
                )
            result.onRight { load() }
        }
    }

    /** Назначает исполнителя [employeeId], либо снимает назначение если null. */
    fun setAssignee(employeeId: EmployeeId?) {
        scope.launch {
            val result =
                if (employeeId != null) {
                    api.tasks.assign(AssignTaskRequest(listOf(taskId), employeeId))
                } else {
                    api.tasks.unassign(UnassignTaskRequest(listOf(taskId)))
                }
            result.onRight { load() }
        }
    }

    /** Обновляет сроки выполнения задачи, сохраняя остальные поля. */
    fun updateDates(dueDate: Instant?, dueDateEnd: Instant?) {
        val task = (state as? TaskDetailState.Loaded)?.task ?: return
        scope.launch {
            api.tasks.update(
                UpdateTaskRequest(
                    id = taskId,
                    title = task.title,
                    description = task.description,
                    clientId = task.clientId,
                    dueDate = dueDate,
                    dueDateEnd = dueDateEnd,
                ),
            ).onRight { load() }
        }
    }

    /** Открывает файл-пикер, загружает файл и прикрепляет его к задаче. */
    fun attachFile() {
        scope.launch {
            val file = pickAnyFile() ?: return@launch
            api.documents.upload(file.first, file.second, file.third).onRight { upload ->
                api.tasks.attach(AttachTaskUploadRequest(taskId, upload.id)).onRight { load() }
            }
        }
    }

    /** Открепляет вложение [uploadId] от задачи. */
    fun detachFile(uploadId: UploadId) {
        scope.launch {
            api.tasks.detach(DetachTaskUploadRequest(taskId, uploadId)).onRight { load() }
        }
    }
}
