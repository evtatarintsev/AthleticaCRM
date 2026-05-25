package org.athletica.crm.api.client

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.athletica.crm.api.schemas.tasks.BulkUpdateTasksAssigneeRequest
import org.athletica.crm.api.schemas.tasks.BulkUpdateTasksResponse
import org.athletica.crm.api.schemas.tasks.BulkUpdateTasksStatusRequest
import org.athletica.crm.api.schemas.tasks.CreateTaskRequest
import org.athletica.crm.api.schemas.tasks.TaskDetailRequest
import org.athletica.crm.api.schemas.tasks.TaskDetailResponse
import org.athletica.crm.api.schemas.tasks.TaskListRequest
import org.athletica.crm.api.schemas.tasks.TaskListResponse
import org.athletica.crm.api.schemas.tasks.UpdateTaskRequest

/** Клиент для работы с задачами через API сервера. */
class TasksApiClient(private val http: HttpClient) {
    /** Возвращает постраничный список задач по параметрам [request]. */
    suspend fun list(request: TaskListRequest): Either<ApiClientError, TaskListResponse> =
        requestCatching {
            http.post("/api/tasks/list") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Возвращает детальную информацию о задаче по [request]. */
    suspend fun detail(request: TaskDetailRequest): Either<ApiClientError, TaskDetailResponse> =
        requestCatching {
            http.post("/api/tasks/detail") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Создаёт новую задачу. */
    suspend fun create(request: CreateTaskRequest): Either<ApiClientError, TaskDetailResponse> =
        requestCatching {
            http.post("/api/tasks/create") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Обновляет задачу (полное новое состояние). */
    suspend fun update(request: UpdateTaskRequest): Either<ApiClientError, TaskDetailResponse> =
        requestCatching {
            http.post("/api/tasks/update") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Массово меняет статус задач. Отклоняется, если нет прав хотя бы к одной задаче. */
    suspend fun bulkUpdateStatus(request: BulkUpdateTasksStatusRequest): Either<ApiClientError, BulkUpdateTasksResponse> =
        requestCatching {
            http.post("/api/tasks/bulk-status") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Массово меняет исполнителя задач. Отклоняется, если нет прав хотя бы к одной задаче. */
    suspend fun bulkUpdateAssignee(request: BulkUpdateTasksAssigneeRequest): Either<ApiClientError, BulkUpdateTasksResponse> =
        requestCatching {
            http.post("/api/tasks/bulk-assignee") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
}
