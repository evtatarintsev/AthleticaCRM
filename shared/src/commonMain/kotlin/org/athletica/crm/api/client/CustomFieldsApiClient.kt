package org.athletica.crm.api.client

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.athletica.crm.api.schemas.customfields.CustomFieldDefinitionSchema
import org.athletica.crm.api.schemas.customfields.SaveCustomFieldsRequest

class CustomFieldsApiClient(private val http: HttpClient) {
    /** Возвращает список кастомных полей для указанного типа сущности. */
    suspend fun list(entityType: String): Either<ApiClientError, List<CustomFieldDefinitionSchema>> = requestCatching { http.get("/api/custom-fields/list?entityType=$entityType") }

    /** Сохраняет полный список кастомных полей для указанного типа сущности. */
    suspend fun save(entityType: String, request: SaveCustomFieldsRequest): Either<ApiClientError, List<CustomFieldDefinitionSchema>> =
        requestCatching {
            http.post("/api/custom-fields/save?entityType=$entityType") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
}
