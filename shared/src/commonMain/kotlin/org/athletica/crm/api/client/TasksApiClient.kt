package org.athletica.crm.api.client

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.athletica.crm.api.schemas.tasks.CreateTaskRequest
import org.athletica.crm.api.schemas.tasks.DetachUploadRequest
import org.athletica.crm.api.schemas.tasks.TaskDetailRequest
import org.athletica.crm.api.schemas.tasks.TaskDetailResponse
import org.athletica.crm.api.schemas.tasks.TaskListRequest
import org.athletica.crm.api.schemas.tasks.TaskListResponse
import org.athletica.crm.api.schemas.tasks.UpdateTaskStatusRequest
import org.athletica.crm.core.tasks.TaskId

class TasksApiClient(private val http: HttpClient) {
    suspend fun list(request: TaskListRequest): Either<ApiClientError, TaskListResponse> =
        requestCatching {
            http.post("/api/tasks/list") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    suspend fun detail(request: TaskDetailRequest): Either<ApiClientError, TaskDetailResponse> =
        requestCatching {
            http.get("/api/tasks/detail") {
                url { parameters.append("taskId", request.taskId.toString()) }
            }
        }

    suspend fun create(request: CreateTaskRequest): Either<ApiClientError, TaskDetailResponse> =
        requestCatching {
            http.post("/api/tasks/create") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    suspend fun updateStatus(request: UpdateTaskStatusRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/tasks/update-status") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    suspend fun detachUpload(request: DetachUploadRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/tasks/detach-upload") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
}
