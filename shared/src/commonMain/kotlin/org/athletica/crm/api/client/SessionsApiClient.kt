package org.athletica.crm.api.client

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.datetime.LocalDate
import org.athletica.crm.api.schemas.sessions.CreateSessionRequest
import org.athletica.crm.api.schemas.sessions.RescheduleSessionRequest
import org.athletica.crm.api.schemas.sessions.SessionDetailResponse
import org.athletica.crm.api.schemas.sessions.SessionListResponse
import org.athletica.crm.api.schemas.sessions.SetSessionEmployeesRequest
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.SessionId

/** API-клиент для работы с занятиями. */
class SessionsApiClient(private val http: HttpClient) {
    /** Возвращает список занятий за период с опциональным фильтром по группе. */
    suspend fun list(
        from: LocalDate,
        to: LocalDate,
        groupId: GroupId? = null,
    ): Either<ApiClientError, SessionListResponse> =
        requestCatching {
            http.get("/api/sessions/list") {
                url {
                    parameters.append("from", from.toString())
                    parameters.append("to", to.toString())
                    groupId?.let { parameters.append("groupId", it.toString()) }
                }
            }
        }

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

    /** Устанавливает преподавателей занятия по идентификатору [id]. */
    suspend fun setEmployees(
        id: SessionId,
        request: SetSessionEmployeesRequest,
    ): Either<ApiClientError, SessionDetailResponse> =
        requestCatching {
            http.post("/api/sessions/$id/set-employees") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
}
