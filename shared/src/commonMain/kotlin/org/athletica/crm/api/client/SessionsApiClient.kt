package org.athletica.crm.api.client

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.athletica.crm.api.schemas.sessions.CreateSessionRequest
import org.athletica.crm.api.schemas.sessions.RescheduleSessionRequest
import org.athletica.crm.api.schemas.sessions.SessionDetailResponse
import org.athletica.crm.api.schemas.sessions.SetSessionEmployeesRequest
import org.athletica.crm.core.entityids.SessionId

/** API-клиент для работы с занятиями. */
class SessionsApiClient(private val http: HttpClient) {
    /** Возвращает детали занятия по идентификатору [id]. */
    suspend fun detail(id: SessionId): Either<ApiClientError, SessionDetailResponse> =
        requestCatching {
            http.get("/api/sessions/$id")
        }

    /** Создаёт разовое занятие. */
    suspend fun create(request: CreateSessionRequest): Either<ApiClientError, SessionDetailResponse> =
        requestCatching {
            http.post("/api/sessions/create") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Отменяет занятие по идентификатору [id]. */
    suspend fun cancel(id: SessionId): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/sessions/$id/cancel")
        }

    /** Переносит занятие по идентификатору [id]. */
    suspend fun reschedule(
        id: SessionId,
        request: RescheduleSessionRequest,
    ): Either<ApiClientError, SessionDetailResponse> =
        requestCatching {
            http.post("/api/sessions/$id/reschedule") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Устанавливает преподавателей занятия. Идентификатор занятия передаётся в [request]. */
    suspend fun setEmployees(request: SetSessionEmployeesRequest): Either<ApiClientError, SessionDetailResponse> =
        requestCatching {
            http.post("/api/sessions/set-employees") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
}
