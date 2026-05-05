package org.athletica.crm.api.client

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.athletica.crm.api.schemas.settings.DisplaySettings

class DisplaySettingsApiClient(private val http: HttpClient) {
    suspend fun get(): Either<ApiClientError, DisplaySettings> = requestCatching { http.get("/api/display-settings") }

    suspend fun update(settings: DisplaySettings): Either<ApiClientError, DisplaySettings> =
        requestCatching {
            http.post("/api/display-settings/update") {
                contentType(ContentType.Application.Json)
                setBody(settings)
            }
        }
}
