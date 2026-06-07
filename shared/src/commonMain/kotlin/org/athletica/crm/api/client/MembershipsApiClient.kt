package org.athletica.crm.api.client

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.athletica.crm.api.schemas.memberships.IssueMembershipRequest
import org.athletica.crm.api.schemas.memberships.MembershipListRequest
import org.athletica.crm.api.schemas.memberships.MembershipListResponse

/**
 * Клиент API выданных абонементов.
 */
class MembershipsApiClient(private val http: HttpClient) {
    /** Выдаёт абонемент клиенту по данным [request]. */
    suspend fun issue(request: IssueMembershipRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/memberships/issue") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Возвращает список абонементов клиента по фильтру [request]. */
    suspend fun list(request: MembershipListRequest): Either<ApiClientError, MembershipListResponse> =
        requestCatching {
            http.post("/api/memberships/list") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
}
