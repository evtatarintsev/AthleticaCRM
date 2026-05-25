package org.athletica.crm.routes.tasks

import org.athletica.crm.api.schemas.tasks.AssignTaskRequest
import org.athletica.crm.api.schemas.tasks.AttachTaskUploadRequest
import org.athletica.crm.api.schemas.tasks.BulkUpdateTasksResponse
import org.athletica.crm.api.schemas.tasks.CreateTaskRequest
import org.athletica.crm.api.schemas.tasks.DetachTaskUploadRequest
import org.athletica.crm.api.schemas.tasks.TaskDetailRequest
import org.athletica.crm.api.schemas.tasks.TaskDetailResponse
import org.athletica.crm.api.schemas.tasks.TaskListItemSchema
import org.athletica.crm.api.schemas.tasks.TaskListRequest
import org.athletica.crm.api.schemas.tasks.TaskListResponse
import org.athletica.crm.api.schemas.tasks.UnassignTaskRequest
import org.athletica.crm.api.schemas.tasks.UpdateTaskRequest
import org.athletica.crm.api.schemas.tasks.UpdateTaskStatusRequest
import org.athletica.crm.api.schemas.upload.UploadResponse
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.entityids.toUploadId
import org.athletica.crm.domain.employees.Employees
import org.athletica.crm.domain.tasks.Task
import org.athletica.crm.domain.tasks.TaskFilter
import org.athletica.crm.domain.tasks.Tasks
import org.athletica.crm.routes.RouteWithContext
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.MinioService
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLong
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import kotlin.time.Duration.Companion.days

/**
 * Регистрирует маршруты для работы с задачами.
 * Требует контекстных параметров [Database], [MinioService] и [Employees].
 */
context(db: Database, minio: MinioService)
fun RouteWithContext.taskRoutes(tasks: Tasks, employees: Employees) {
    post<TaskListRequest, TaskListResponse>("/tasks/list") { req ->
        db.transaction {
            val filter = req.toFilter()
            val result = tasks.list(filter)
            val employeeIds =
                (result.items.mapNotNull { it.assigneeId } + result.items.map { it.createdBy })
                    .toSet()
            val clientIds = result.items.mapNotNull { it.clientId }.toSet()
            val employeeNames = loadEmployeeNames(employeeIds)
            val clientNames = loadClientNames(clientIds)
            TaskListResponse(
                tasks = result.items.map { it.toListItem(employeeNames, clientNames) },
                total = result.total,
            )
        }
    }

    post<TaskDetailRequest, TaskDetailResponse>("/tasks/detail") { req ->
        db.transaction {
            val task = tasks.byId(req.taskId)
            val employeeIds = listOfNotNull(task.createdBy, task.assigneeId).toSet()
            val clientIds = listOfNotNull(task.clientId).toSet()
            val employeeNames = loadEmployeeNames(employeeIds)
            val clientNames = loadClientNames(clientIds)
            val attachmentResponses = loadUploadResponses(task.attachments.map { it.value }, minio)
            task.toDetailResponse(employeeNames, clientNames, attachmentResponses)
        }
    }

    post<CreateTaskRequest, TaskDetailResponse>("/tasks/create") { req ->
        db.transaction {
            val task =
                tasks.new(
                    id = req.id,
                    title = req.title,
                    description = req.description,
                    clientId = req.clientId,
                    dueDate = req.dueDate,
                    dueDateEnd = req.dueDateEnd,
                )
            val employeeIds = setOf(task.createdBy)
            val clientIds = listOfNotNull(task.clientId).toSet()
            val employeeNames = loadEmployeeNames(employeeIds)
            val clientNames = loadClientNames(clientIds)
            task.toDetailResponse(employeeNames, clientNames, emptyList())
        }
    }

    post<UpdateTaskRequest, TaskDetailResponse>("/tasks/update") { req ->
        db.transaction {
            val updated =
                tasks.byId(req.id).withNew(
                    newTitle = req.title,
                    newDescription = req.description,
                    newClientId = req.clientId,
                    newDueDate = req.dueDate,
                    newDueDateEnd = req.dueDateEnd,
                )
            updated.save()
            val employeeIds = listOfNotNull(updated.createdBy, updated.assigneeId).toSet()
            val clientIds = listOfNotNull(updated.clientId).toSet()
            val employeeNames = loadEmployeeNames(employeeIds)
            val clientNames = loadClientNames(clientIds)
            val attachmentResponses = loadUploadResponses(updated.attachments.map { it.value }, minio)
            updated.toDetailResponse(employeeNames, clientNames, attachmentResponses)
        }
    }

    post<UpdateTaskStatusRequest, BulkUpdateTasksResponse>("/tasks/status") { req ->
        db.transaction {
            tasks.byIds(req.taskIds)
                .map { it.status(req.status) }
                .forEach { it.save() }
            BulkUpdateTasksResponse(updated = req.taskIds.size)
        }
    }

    post<AssignTaskRequest, BulkUpdateTasksResponse>("/tasks/assign") { req ->
        db.transaction {
            val employee = employees.byId(req.assigneeId)
            tasks.byIds(req.taskIds)
                .map { it.assignTo(employee) }
                .forEach { it.save() }
            BulkUpdateTasksResponse(updated = req.taskIds.size)
        }
    }

    post<UnassignTaskRequest, BulkUpdateTasksResponse>("/tasks/unassign") { req ->
        db.transaction {
            tasks.byIds(req.taskIds)
                .map { it.unassign() }
                .forEach { it.save() }
            BulkUpdateTasksResponse(updated = req.taskIds.size)
        }
    }

    post<AttachTaskUploadRequest, TaskDetailResponse>("/tasks/attach") { req ->
        db.transaction {
            val updated = tasks.byId(req.taskId).attach(req.uploadId)
            updated.save()
            val employeeIds = listOfNotNull(updated.createdBy, updated.assigneeId).toSet()
            val clientIds = listOfNotNull(updated.clientId).toSet()
            val employeeNames = loadEmployeeNames(employeeIds)
            val clientNames = loadClientNames(clientIds)
            val attachmentResponses = loadUploadResponses(updated.attachments.map { it.value }, minio)
            updated.toDetailResponse(employeeNames, clientNames, attachmentResponses)
        }
    }

    post<DetachTaskUploadRequest, TaskDetailResponse>("/tasks/detach") { req ->
        db.transaction {
            val updated = tasks.byId(req.taskId).detach(req.uploadId)
            updated.save()
            val employeeIds = listOfNotNull(updated.createdBy, updated.assigneeId).toSet()
            val clientIds = listOfNotNull(updated.clientId).toSet()
            val employeeNames = loadEmployeeNames(employeeIds)
            val clientNames = loadClientNames(clientIds)
            val attachmentResponses = loadUploadResponses(updated.attachments.map { it.value }, minio)
            updated.toDetailResponse(employeeNames, clientNames, attachmentResponses)
        }
    }
}

/** Загружает имена сотрудников по набору идентификаторов. */
private suspend fun Transaction.loadEmployeeNames(ids: Set<EmployeeId>): Map<EmployeeId, String> {
    if (ids.isEmpty()) return emptyMap()
    return sql("SELECT id, name FROM employees WHERE id = ANY(:ids)")
        .bind("ids", ids.toList())
        .list { row ->
            val id = row.asUuid("id").toEmployeeId()
            val name = row.asString("name")
            id to name
        }
        .toMap()
}

/** Загружает имена клиентов по набору идентификаторов. */
private suspend fun Transaction.loadClientNames(ids: Set<ClientId>): Map<ClientId, String> {
    if (ids.isEmpty()) return emptyMap()
    return sql("SELECT id, name FROM clients WHERE id = ANY(:ids)")
        .bind("ids", ids.toList())
        .list { row ->
            val id = row.asUuid("id").toClientId()
            val name = row.asString("name")
            id to name
        }
        .toMap()
}

/** Загружает метаданные загрузок и генерирует presigned URL через [minio]. */
private suspend fun Transaction.loadUploadResponses(
    uploadIds: List<kotlin.uuid.Uuid>,
    minio: MinioService,
): List<UploadResponse> {
    if (uploadIds.isEmpty()) return emptyList()
    val ttl = 7.days

    data class UploadRow(
        val id: kotlin.uuid.Uuid,
        val objectKey: String,
        val originalName: String,
        val contentType: String,
        val sizeBytes: Long,
    )
    val rows =
        sql(
            "SELECT id, object_key, original_name, content_type, size_bytes FROM uploads WHERE id = ANY(:ids)",
        )
            .bind("ids", uploadIds)
            .list { row ->
                UploadRow(
                    id = row.asUuid("id"),
                    objectKey = row.asString("object_key"),
                    originalName = row.asString("original_name"),
                    contentType = row.asString("content_type"),
                    sizeBytes = row.asLong("size_bytes"),
                )
            }
    return rows.map { r ->
        UploadResponse(
            id = r.id.toUploadId(),
            url = minio.presignedGetUrl(r.objectKey, ttlSeconds = ttl.inWholeSeconds.toInt()),
            originalName = r.originalName,
            contentType = r.contentType,
            sizeBytes = r.sizeBytes,
        )
    }
}

private fun TaskListRequest.toFilter() =
    TaskFilter(
        onlyMine = onlyMine,
        statuses = statuses.toSet(),
        dueDateFrom = dueDateFrom,
        dueDateTo = dueDateTo,
        clientId = clientId,
        searchText = searchText?.takeIf { it.isNotBlank() },
        limit = limit,
        offset = offset,
    )

private fun Task.toListItem(
    employeeNames: Map<EmployeeId, String>,
    clientNames: Map<ClientId, String>,
) = TaskListItemSchema(
    id = id,
    title = title,
    assigneeId = assigneeId,
    assigneeName = assigneeId?.let { employeeNames[it] },
    clientId = clientId,
    clientName = clientId?.let { clientNames[it] },
    status = status,
    dueDate = dueDate,
    dueDateEnd = dueDateEnd,
)

private fun Task.toDetailResponse(
    employeeNames: Map<EmployeeId, String>,
    clientNames: Map<ClientId, String>,
    attachments: List<UploadResponse>,
) = TaskDetailResponse(
    id = id,
    createdBy = createdBy,
    createdByName = employeeNames[createdBy] ?: "",
    assigneeId = assigneeId,
    assigneeName = assigneeId?.let { employeeNames[it] },
    clientId = clientId,
    clientName = clientId?.let { clientNames[it] },
    title = title,
    description = description,
    status = status,
    dueDate = dueDate,
    dueDateEnd = dueDateEnd,
    completedAt = completedAt,
    createdAt = createdAt,
    attachments = attachments,
)
