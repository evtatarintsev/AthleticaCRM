package org.athletica.crm.api.schemas.tasks

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.core.tasks.TaskStatus

@Serializable
data class TaskDetailResponse(
    val id: TaskId,
    val createdBy: EmployeeId,
    val assignee: EmployeeId?,
    val clientId: ClientId?,
    val title: String,
    val description: String,
    val status: TaskStatus,
    val dueDate: String?,
    val dueDateEnd: String?,
    val completedAt: String?,
    val createdAt: String,
    val attachments: List<UploadId>,
)

@Serializable
data class TaskListItemSchema(
    val id: TaskId,
    val title: String,
    val assigneeName: String?,
    val clientName: String?,
    val status: TaskStatus,
    val dueDate: String?,
    val isOverdue: Boolean,
)

@Serializable
data class TaskListResponse(
    val tasks: List<TaskListItemSchema>,
    val total: UInt,
)
