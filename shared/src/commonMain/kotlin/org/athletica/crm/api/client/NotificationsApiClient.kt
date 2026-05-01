package org.athletica.crm.api.client

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.athletica.crm.api.schemas.notifications.MarkNotificationsReadRequest
import org.athletica.crm.api.schemas.notifications.NotificationsResponse

class NotificationsApiClient(private val http: HttpClient) {
    /**
     * Возвращает уведомления текущего пользователя.
     * [isRead] — опциональный фильтр: `true` — только прочитанные, `false` — только непрочитанные,
     * `null` — все.
     */
    suspend fun list(isRead: Boolean? = null): Either<ApiClientError, NotificationsResponse> =
        requestCatching {
            http.get("/api/notifications") {
                url {
                    if (isRead != null) parameters.append("isRead", isRead.toString())
                }
            }
        }

    /** Отмечает уведомления из [request] прочитанными для текущего пользователя. */
    suspend fun markRead(request: MarkNotificationsReadRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/notifications/mark-as-read") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Отмечает все уведомления текущего пользователя прочитанными. */
    suspend fun markAllRead(): Either<ApiClientError, Unit> = requestCatching { http.post("/api/notifications/mark-all-read") }
}
