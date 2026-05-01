package org.athletica.crm.api.client

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.athletica.crm.api.schemas.branches.BranchCreateRequest
import org.athletica.crm.api.schemas.branches.BranchListResponse
import org.athletica.crm.api.schemas.branches.BranchUpdateRequest
import org.athletica.crm.api.schemas.branches.DeleteBranchRequest

class BranchesApiClient(private val http: HttpClient) {
    /** Возвращает список филиалов организации. */
    suspend fun list(): Either<ApiClientError, BranchListResponse> = requestCatching { http.get("/api/branches/list") }

    /** Создаёт новый филиал по данным [request]. */
    suspend fun create(request: BranchCreateRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/branches/create") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Обновляет существующий филиал по данным [request]. */
    suspend fun update(request: BranchUpdateRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/branches/update") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Удаляет филиалы по списку id из [request]. Атомарная операция. */
    suspend fun delete(request: DeleteBranchRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/branches/delete") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
}
