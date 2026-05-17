package org.athletica.crm.api.schemas.tasks

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.core.tasks.TaskStatus

@Serializable
data class CreateTaskRequest(
    val id: TaskId,
    val title: String,
    val description: String,
    val assigneeId: EmployeeId?,
    val clientId: ClientId?,
    val dueDate: String?,
    val dueDateEnd: String?,
    val attachments: List<UploadId> = emptyList(),
)

@Serializable
data class UpdateTaskStatusRequest(
    val taskId: TaskId,
    val status: TaskStatus,
)

@Serializable
data class TaskListRequest(
    val assigneeId: EmployeeId? = null,
    val status: List<TaskStatus> = emptyList(),
    val dueDateFrom: String? = null,
    val dueDateTo: String? = null,
    val clientId: ClientId? = null,
    val searchText: String? = null,
)

@Serializable
data class TaskDetailRequest(
    val taskId: TaskId,
)

@Serializable
data class DetachUploadRequest(
    val taskId: TaskId,
    val uploadId: UploadId,
)
