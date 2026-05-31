package org.athletica.crm.api.client

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.athletica.crm.api.schemas.channels.ChannelListResponse
import org.athletica.crm.api.schemas.channels.CreateChannelIntegrationRequest
import org.athletica.crm.api.schemas.channels.DeleteChannelIntegrationRequest
import org.athletica.crm.api.schemas.channels.UpdateChannelIntegrationRequest

/** Клиент API для настройки интеграций каналов связи организации. */
class ChannelsApiClient(private val http: HttpClient) {
    /** Возвращает список настроенных интеграций каналов организации. */
    suspend fun list(): Either<ApiClientError, ChannelListResponse> = requestCatching { http.get("/api/channels/list") }

    /** Создаёт новую интеграцию канала по данным [request]. */
    suspend fun create(request: CreateChannelIntegrationRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/channels/create") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Обновляет интеграцию канала по данным [request]. */
    suspend fun update(request: UpdateChannelIntegrationRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/channels/update") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Удаляет интеграцию канала по данным [request]. */
    suspend fun delete(request: DeleteChannelIntegrationRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/channels/delete") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
}
