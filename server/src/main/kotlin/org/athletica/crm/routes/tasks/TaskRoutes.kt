package org.athletica.crm.routes.tasks

import org.athletica.crm.api.schemas.tasks.AssignTaskRequest
import org.athletica.crm.api.schemas.tasks.AttachTaskUploadRequest
import org.athletica.crm.api.schemas.tasks.BulkUpdateTasksResponse
import org.athletica.crm.api.schemas.tasks.CreateTaskRequest
import org.athletica.crm.api.schemas.tasks.DetachTaskUploadRequest
import org.athletica.crm.api.schemas.tasks.TaskDetailRequest
import org.athletica.crm.api.schemas.tasks.TaskDetailResponse
import org.athletica.crm.api.schemas.tasks.TaskListRequest
import org.athletica.crm.api.schemas.tasks.TaskListResponse
import org.athletica.crm.api.schemas.tasks.UnassignTaskRequest
import org.athletica.crm.api.schemas.tasks.UpdateTaskRequest
import org.athletica.crm.api.schemas.tasks.UpdateTaskStatusRequest
import org.athletica.crm.api.schemas.upload.UploadResponse
import org.athletica.crm.domain.employees.Employees
import org.athletica.crm.domain.tasks.Tasks
import org.athletica.crm.read.ReadViews
import org.athletica.crm.read.tasks.TaskDetailRow
import org.athletica.crm.read.tasks.TaskListQuery
import org.athletica.crm.read.uploads.UploadRow
import org.athletica.crm.routes.RouteWithContext
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.MinioService
import kotlin.time.Duration.Companion.days

/**
 * Регистрирует маршруты для работы с задачами.
 * Требует контекстных параметров [Database] и [MinioService].
 */
context(db: Database, minio: MinioService)
fun RouteWithContext.taskRoutes(tasks: Tasks, employees: Employees, views: ReadViews) {
    post<TaskListRequest, TaskListResponse>("/tasks/list") { req ->
        db.transaction {
            views.taskList.page(req.toQuery())
        }
    }

    post<TaskDetailRequest, TaskDetailResponse>("/tasks/detail") { req ->
        db
            .transaction { views.taskDetail.byId(req.taskId) }
            .toResponse(minio)
    }

    post<CreateTaskRequest, TaskDetailResponse>("/tasks/create") { req ->
        db
            .transaction {
                val created =
                    tasks.new(
                        id = req.id,
                        title = req.title,
                        description = req.description,
                        clientId = req.clientId,
                        dueDate = req.dueDate,
                        dueDateEnd = req.dueDateEnd,
                    )
                req.assigneeId?.let { assigneeId ->
                    created.assignTo(employees.byId(assigneeId)).save()
                }
                views.taskDetail.byId(created.id)
            }
            .toResponse(minio)
    }

    post<UpdateTaskRequest, TaskDetailResponse>("/tasks/update") { req ->
        db
            .transaction {
                tasks
                    .byId(req.id)
                    .withNew(
                        newTitle = req.title,
                        newDescription = req.description,
                        newClientId = req.clientId,
                        newDueDate = req.dueDate,
                        newDueDateEnd = req.dueDateEnd,
                    )
                    .save()
                views.taskDetail.byId(req.id)
            }
            .toResponse(minio)
    }

    post<UpdateTaskStatusRequest, BulkUpdateTasksResponse>("/tasks/status") { req ->
        db.transaction {
            tasks
                .byIds(req.taskIds)
                .map { it.status(req.status) }
                .forEach { it.save() }
            BulkUpdateTasksResponse(updated = req.taskIds.size)
        }
    }

    post<AssignTaskRequest, BulkUpdateTasksResponse>("/tasks/assign") { req ->
        db.transaction {
            val employee = employees.byId(req.assigneeId)
            tasks
                .byIds(req.taskIds)
                .map { it.assignTo(employee) }
                .forEach { it.save() }
            BulkUpdateTasksResponse(updated = req.taskIds.size)
        }
    }

    post<UnassignTaskRequest, BulkUpdateTasksResponse>("/tasks/unassign") { req ->
        db.transaction {
            tasks
                .byIds(req.taskIds)
                .map { it.unassign() }
                .forEach { it.save() }
            BulkUpdateTasksResponse(updated = req.taskIds.size)
        }
    }

    post<AttachTaskUploadRequest, TaskDetailResponse>("/tasks/attach") { req ->
        db
            .transaction {
                tasks.byId(req.taskId).attach(req.uploadId).save()
                views.taskDetail.byId(req.taskId)
            }
            .toResponse(minio)
    }

    post<DetachTaskUploadRequest, TaskDetailResponse>("/tasks/detach") { req ->
        db
            .transaction {
                tasks.byId(req.taskId).detach(req.uploadId).save()
                views.taskDetail.byId(req.taskId)
            }
            .toResponse(minio)
    }
}

/** Срок жизни подписанной ссылки на вложение. */
private val ATTACHMENT_URL_TTL = 7.days

/** Преобразует запрос списка задач в параметры выборки. */
private fun TaskListRequest.toQuery() =
    TaskListQuery(
        onlyMine = onlyMine,
        statuses = statuses.toSet(),
        dueDateFrom = dueDateFrom,
        dueDateTo = dueDateTo,
        clientId = clientId,
        searchText = searchText?.takeIf { it.isNotBlank() },
        limit = limit,
        offset = offset,
    )

/** Дополняет карточку задачи подписанными ссылками на вложения. */
private suspend fun TaskDetailRow.toResponse(minio: MinioService) =
    TaskDetailResponse(
        id = id,
        createdBy = createdBy,
        createdByName = createdByName,
        assigneeId = assigneeId,
        assigneeName = assigneeName,
        clientId = clientId,
        clientName = clientName,
        title = title,
        description = description,
        status = status,
        dueDate = dueDate,
        dueDateEnd = dueDateEnd,
        completedAt = completedAt,
        createdAt = createdAt,
        attachments = attachments.map { it.toResponse(minio) },
    )

/** Подписывает ссылку на скачивание файла [UploadRow]. */
private suspend fun UploadRow.toResponse(minio: MinioService) =
    UploadResponse(
        id = id,
        url = minio.presignedGetUrl(objectKey, ttlSeconds = ATTACHMENT_URL_TTL.inWholeSeconds.toInt()),
        originalName = originalName,
        contentType = contentType,
        sizeBytes = sizeBytes,
    )
