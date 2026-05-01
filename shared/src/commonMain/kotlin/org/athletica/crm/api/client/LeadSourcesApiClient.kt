package org.athletica.crm.api.client

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.athletica.crm.api.schemas.leadSources.CreateLeadSourceRequest
import org.athletica.crm.api.schemas.leadSources.DeleteLeadSourceRequest
import org.athletica.crm.api.schemas.leadSources.LeadSourceListResponse
import org.athletica.crm.api.schemas.leadSources.UpdateLeadSourceRequest

class LeadSourcesApiClient(private val http: HttpClient) {
    /** Возвращает список источников клиентов организации. */
    suspend fun list(): Either<ApiClientError, LeadSourceListResponse> = requestCatching { http.get("/api/lead-sources/list") }

    /** Создаёт новый источник клиента по данным [request]. */
    suspend fun create(request: CreateLeadSourceRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/lead-sources/create") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Обновляет название источника клиента по данным [request]. */
    suspend fun update(request: UpdateLeadSourceRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/lead-sources/update") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Удаляет источники клиентов по списку id из [request]. Атомарная операция. */
    suspend fun delete(request: DeleteLeadSourceRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/lead-sources/delete") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
}
