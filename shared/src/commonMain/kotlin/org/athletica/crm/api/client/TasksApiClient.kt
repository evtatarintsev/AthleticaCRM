package org.athletica.crm.api.client

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
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

    /** Создаёт новую задачу без исполнителя и вложений. */
    suspend fun create(request: CreateTaskRequest): Either<ApiClientError, TaskDetailResponse> =
        requestCatching {
            http.post("/api/tasks/create") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Обновляет поля задачи (заголовок, описание, клиент, даты). */
    suspend fun update(request: UpdateTaskRequest): Either<ApiClientError, TaskDetailResponse> =
        requestCatching {
            http.post("/api/tasks/update") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Меняет статус одной или нескольких задач. Отклоняется, если нет прав хотя бы к одной. */
    suspend fun updateStatus(request: UpdateTaskStatusRequest): Either<ApiClientError, BulkUpdateTasksResponse> =
        requestCatching {
            http.post("/api/tasks/status") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Назначает исполнителя одной или нескольким задачам. Отклоняется, если нет прав хотя бы к одной. */
    suspend fun assign(request: AssignTaskRequest): Either<ApiClientError, BulkUpdateTasksResponse> =
        requestCatching {
            http.post("/api/tasks/assign") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Снимает исполнителя с одной или нескольких задач. Отклоняется, если нет прав хотя бы к одной. */
    suspend fun unassign(request: UnassignTaskRequest): Either<ApiClientError, BulkUpdateTasksResponse> =
        requestCatching {
            http.post("/api/tasks/unassign") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Прикрепляет файл к задаче. */
    suspend fun attach(request: AttachTaskUploadRequest): Either<ApiClientError, TaskDetailResponse> =
        requestCatching {
            http.post("/api/tasks/attach") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Открепляет файл от задачи. */
    suspend fun detach(request: DetachTaskUploadRequest): Either<ApiClientError, TaskDetailResponse> =
        requestCatching {
            http.post("/api/tasks/detach") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
}
