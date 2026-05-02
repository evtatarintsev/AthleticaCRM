package org.athletica.crm.api.client

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import org.athletica.crm.api.schemas.home.TodaySessionsResponse

class HomeApiClient(private val http: HttpClient) {
    /** Возвращает занятия на текущую дату для главной страницы. */
    suspend fun todaySessions(): Either<ApiClientError, TodaySessionsResponse> = requestCatching { http.get("/api/home/today-sessions") }
}
