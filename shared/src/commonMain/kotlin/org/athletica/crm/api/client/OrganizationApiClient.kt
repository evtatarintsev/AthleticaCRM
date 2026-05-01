package org.athletica.crm.api.client

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.athletica.crm.api.schemas.org.OrgSettingsResponse
import org.athletica.crm.api.schemas.org.UpdateOrgSettingsRequest

class OrganizationApiClient(private val http: HttpClient) {
    /** Возвращает основные настройки организации (название, часовой пояс). */
    suspend fun settings(): Either<ApiClientError, OrgSettingsResponse> = requestCatching { http.get("/api/org/settings") }

    /** Обновляет название и часовой пояс организации по данным [request]. */
    suspend fun updateSettings(request: UpdateOrgSettingsRequest): Either<ApiClientError, OrgSettingsResponse> =
        requestCatching {
            http.post("/api/org/settings/update") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
}
