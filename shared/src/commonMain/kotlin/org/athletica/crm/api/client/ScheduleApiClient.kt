package org.athletica.crm.api.client

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.athletica.crm.api.schemas.schedule.ScheduleListRequest
import org.athletica.crm.api.schemas.schedule.ScheduleListResponse

/** API-клиент раздела «Расписание». */
class ScheduleApiClient(private val http: HttpClient) {
    /** Возвращает занятия текущего филиала за период и с фильтрами из [request]. */
    suspend fun list(request: ScheduleListRequest): Either<ApiClientError, ScheduleListResponse> =
        requestCatching {
            http.post("/api/schedule/list") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
}
