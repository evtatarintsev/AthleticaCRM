package org.athletica.crm.api.client

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.athletica.crm.api.schemas.disciplines.CreateDisciplineRequest
import org.athletica.crm.api.schemas.disciplines.DeleteDisciplineRequest
import org.athletica.crm.api.schemas.disciplines.DisciplineListResponse
import org.athletica.crm.api.schemas.disciplines.UpdateDisciplineRequest

class DisciplinesApiClient(private val http: HttpClient) {
    /** Возвращает список дисциплин организации. */
    suspend fun list(): Either<ApiClientError, DisciplineListResponse> = requestCatching { http.get("/api/disciplines/list") }

    /** Создаёт новую дисциплину по данным [request]. Возвращает созданную дисциплину. */
    suspend fun create(request: CreateDisciplineRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/disciplines/create") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Обновляет название дисциплины по данным [request]. Возвращает обновлённую дисциплину. */
    suspend fun update(request: UpdateDisciplineRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/disciplines/update") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Удаляет дисциплины по списку id из [request]. Атомарная операция. */
    suspend fun delete(request: DeleteDisciplineRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/disciplines/delete") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
}
