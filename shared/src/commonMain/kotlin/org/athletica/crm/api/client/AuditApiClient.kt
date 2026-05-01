package org.athletica.crm.api.client

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import org.athletica.crm.api.schemas.audit.AuditLogListResponse

class AuditApiClient(private val http: HttpClient) {
    /** Возвращает страницу лога аудита с пагинацией и фильтрами. */
    suspend fun logList(
        page: Int = 0,
        pageSize: Int = 50,
        actionType: String? = null,
        userId: String? = null,
        entityType: String? = null,
        from: String? = null,
        to: String? = null,
    ): Either<ApiClientError, AuditLogListResponse> =
        requestCatching {
            http.get("/api/audit/log") {
                url {
                    parameters.append("page", page.toString())
                    parameters.append("pageSize", pageSize.toString())
                    if (actionType != null) {
                        parameters.append("actionType", actionType)
                    }
                    if (userId != null) {
                        parameters.append("userId", userId)
                    }
                    if (entityType != null) {
                        parameters.append("entityType", entityType)
                    }
                    if (from != null) {
                        parameters.append("from", from)
                    }
                    if (to != null) {
                        parameters.append("to", to)
                    }
                }
            }
        }
}
