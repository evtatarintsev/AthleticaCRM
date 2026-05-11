package org.athletica.crm.api.client

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import org.athletica.crm.api.schemas.orgbalance.OrgBalanceDetailResponse

/** Клиент API для работы с балансом организации. */
class OrgBalanceApiClient(private val http: HttpClient) {
    /** Возвращает текущий баланс и историю операций организации. */
    suspend fun detail(): Either<ApiClientError, OrgBalanceDetailResponse> = requestCatching { http.get("/api/org-balance/detail") }
}
