package org.athletica.crm.routes

import arrow.core.raise.Raise
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.athletica.crm.api.schemas.tasks.CreateTaskRequest
import org.athletica.crm.api.schemas.tasks.DetachUploadRequest
import org.athletica.crm.api.schemas.tasks.TaskDetailRequest
import org.athletica.crm.api.schemas.tasks.TaskDetailResponse
import org.athletica.crm.api.schemas.tasks.TaskListItemSchema
import org.athletica.crm.api.schemas.tasks.TaskListRequest
import org.athletica.crm.api.schemas.tasks.TaskListResponse
import org.athletica.crm.api.schemas.tasks.UpdateTaskStatusRequest
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.tasks.TaskStatus
import org.athletica.crm.domain.clients.Clients
import org.athletica.crm.domain.employees.Employees
import org.athletica.crm.domain.tasks.TaskFilter
import org.athletica.crm.domain.tasks.Tasks
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.Transaction
import kotlin.time.Clock

context(db: Database)
fun RouteWithContext.taskRoutes(
    tasks: Tasks,
    employees: Employees,
    clients: Clients,
    uploads: Uploads,
) {
    post<TaskListRequest, TaskListResponse>("/tasks/list") { request ->
        db.transaction { tr ->
            context(ctx, tr, this) {
                val taskList =
                    tasks.list(
                        TaskFilter(
                            assigneeId = request.assigneeId,
                            createdBy = null,
                            status = request.status.ifEmpty { null }?.toSet(),
                            dueDateFrom = request.dueDateFrom?.let { kotlin.time.Instant.parse(it) },
                            dueDateTo = request.dueDateTo?.let { kotlin.time.Instant.parse(it) },
                            clientId = request.clientId,
                            searchText = request.searchText,
                        ),
                    )

                val employeeIds = taskList.mapNotNull { it.assigneeId }.distinct()
                val clientIds = taskList.mapNotNull { it.clientId }.distinct()

                val employeesById = employees.byIdSet(employeeIds.toSet())
                val clientsById = clients.byIdSet(clientIds.toSet())

                val now = Clock.System.now()
                val tz = TimeZone.of("Europe/Moscow")

                val items =
                    taskList.map { task ->
                        val assigneeName = task.assigneeId?.let { employeesById[it]?.name }
                        val clientName = task.clientId?.let { clientsById[it]?.name }
                        val isOverdue =
                            task.dueDate?.let { dueDate ->
                                val dueLocal = dueDate.toLocalDateTime(tz).date
                                val nowLocal = now.toLocalDateTime(tz).date
                                dueLocal < nowLocal && task.status != TaskStatus.COMPLETED
                            } ?: false

                        TaskListItemSchema(
                            id = task.id,
                            title = task.title,
                            assigneeName = assigneeName,
                            clientName = clientName,
                            status = task.status,
                            dueDate = task.dueDate?.toString(),
                            isOverdue = isOverdue,
                        )
                    }

                TaskListResponse(tasks = items, total = items.size.toUInt())
            }
        }
    }

    get<TaskDetailRequest, TaskDetailResponse>("/tasks/detail") { request ->
        db.transaction { tr ->
            context(ctx, tr, this) {
                val task = tasks.byId(request.taskId)
                val attachmentIds = task.attachmentUploadIds()

                TaskDetailResponse(
                    id = task.id,
                    createdBy = task.createdBy,
                    assignee = task.assigneeId,
                    clientId = task.clientId,
                    title = task.title,
                    description = task.description,
                    status = task.status,
                    dueDate = task.dueDate?.toString(),
                    dueDateEnd = task.dueDateEnd?.toString(),
                    completedAt = task.completedAt?.toString(),
                    createdAt = task.createdAt.toString(),
                    attachments = attachmentIds,
                )
            }
        }
    }

    post<CreateTaskRequest, TaskDetailResponse>("/tasks/create") { request ->
        db.transaction { tr ->
            context(ctx, tr, this) {
                val task =
                    tasks.new(
                        id = request.id,
                        orgId = ctx.orgId,
                        createdBy = ctx.employeeId,
                        title = request.title,
                        description = request.description,
                        assigneeId = request.assigneeId,
                        clientId = request.clientId,
                        dueDate = request.dueDate?.let { kotlin.time.Instant.parse(it) },
                        dueDateEnd = request.dueDateEnd?.let { kotlin.time.Instant.parse(it) },
                    )

                request.attachments.forEach { uploadId ->
                    task.attachUpload(uploadId)
                }

                val attachmentIds = task.attachmentUploadIds()

                TaskDetailResponse(
                    id = task.id,
                    createdBy = task.createdBy,
                    assignee = task.assigneeId,
                    clientId = task.clientId,
                    title = task.title,
                    description = task.description,
                    status = task.status,
                    dueDate = task.dueDate?.toString(),
                    dueDateEnd = task.dueDateEnd?.toString(),
                    completedAt = task.completedAt?.toString(),
                    createdAt = task.createdAt.toString(),
                    attachments = attachmentIds,
                )
            }
        }
    }

    post<UpdateTaskStatusRequest, Unit>("/tasks/update-status") { request ->
        db.transaction { tr ->
            context(ctx, tr, this) {
                val task = tasks.byId(request.taskId)
                task.updateStatus(request.status)
            }
        }
    }

    post<DetachUploadRequest, Unit>("/tasks/detach-upload") { request ->
        db.transaction { tr ->
            context(ctx, tr, this) {
                val task = tasks.byId(request.taskId)
                task.detachUpload(request.uploadId)
            }
        }
    }
}

private suspend fun Clients.byIdSet(ids: Set<org.athletica.crm.core.entityids.ClientId>): Map<org.athletica.crm.core.entityids.ClientId, org.athletica.crm.domain.clients.Client> {
    return ids.associateWith { byId(it) }
}

private suspend fun Employees.byIdSet(ids: Set<org.athletica.crm.core.entityids.EmployeeId>): Map<org.athletica.crm.core.entityids.EmployeeId, org.athletica.crm.domain.employees.Employee> {
    return ids.associateWith { byId(it) }
}
