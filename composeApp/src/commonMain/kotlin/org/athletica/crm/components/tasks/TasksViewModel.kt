package org.athletica.crm.components.tasks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.tasks.CreateTaskRequest
import org.athletica.crm.api.schemas.tasks.DetachUploadRequest
import org.athletica.crm.api.schemas.tasks.TaskDetailRequest
import org.athletica.crm.api.schemas.tasks.TaskDetailResponse
import org.athletica.crm.api.schemas.tasks.TaskListItemSchema
import org.athletica.crm.api.schemas.tasks.TaskListRequest
import org.athletica.crm.api.schemas.tasks.UpdateTaskStatusRequest
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.core.tasks.TaskStatus

sealed class TasksState {
    data object Loading : TasksState()
    data class Loaded(val tasks: List<TaskListItemSchema>) : TasksState()
    data class Error(val error: TasksApiError) : TasksState()
}

sealed class TaskDetailState {
    data object Loading : TaskDetailState()
    data class Loaded(val task: TaskDetailResponse) : TaskDetailState()
    data class Error(val error: TasksApiError) : TaskDetailState()
}

class TasksViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
) {
    var state by mutableStateOf<TasksState>(TasksState.Loading)
        private set

    var detailState by mutableStateOf<TaskDetailState>(TaskDetailState.Loading)
        private set

    fun load(request: TaskListRequest = TaskListRequest()) {
        scope.launch {
            state = TasksState.Loading
            api.tasks.list(request).fold(
                ifLeft = { state = TasksState.Error(it.toTasksApiError()) },
                ifRight = { state = TasksState.Loaded(it.tasks) },
            )
        }
    }

    fun loadDetail(taskId: TaskId) {
        scope.launch {
            detailState = TaskDetailState.Loading
            api.tasks.detail(TaskDetailRequest(taskId)).fold(
                ifLeft = { detailState = TaskDetailState.Error(it.toTasksApiError()) },
                ifRight = { detailState = TaskDetailState.Loaded(it) },
            )
        }
    }

    fun createTask(request: CreateTaskRequest, onSuccess: (TaskDetailResponse) -> Unit) {
        scope.launch {
            api.tasks.create(request).fold(
                ifLeft = { /* handle error */ },
                ifRight = { onSuccess(it) },
            )
        }
    }

    fun updateStatus(taskId: TaskId, status: TaskStatus) {
        scope.launch {
            api.tasks.updateStatus(UpdateTaskStatusRequest(taskId, status)).fold(
                ifLeft = { /* handle error */ },
                ifRight = { load() },
            )
        }
    }

    fun detachUpload(taskId: TaskId, uploadId: org.athletica.crm.core.entityids.UploadId) {
        scope.launch {
            api.tasks.detachUpload(DetachUploadRequest(taskId, uploadId)).fold(
                ifLeft = { /* handle error */ },
                ifRight = { loadDetail(taskId) },
            )
        }
    }
}
