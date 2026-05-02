package org.athletica.crm.api.client

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.athletica.crm.api.schemas.halls.CreateHallRequest
import org.athletica.crm.api.schemas.halls.DeleteHallRequest
import org.athletica.crm.api.schemas.halls.HallListResponse
import org.athletica.crm.api.schemas.halls.UpdateHallRequest

class HallsApiClient(private val http: HttpClient) {
    /** Возвращает список залов текущего филиала. */
    suspend fun list(): Either<ApiClientError, HallListResponse> = requestCatching { http.get("/api/halls/list") }

    /** Создаёт новый зал по данным [request]. */
    suspend fun create(request: CreateHallRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/halls/create") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Обновляет существующий зал по данным [request]. */
    suspend fun update(request: UpdateHallRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/halls/update") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Удаляет залы по списку id из [request]. */
    suspend fun delete(request: DeleteHallRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/halls/delete") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
}
