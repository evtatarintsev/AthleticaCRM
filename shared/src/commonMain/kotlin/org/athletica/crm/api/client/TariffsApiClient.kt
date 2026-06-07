package org.athletica.crm.api.client

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.athletica.crm.api.schemas.tariffs.ArchiveTariffPlanRequest
import org.athletica.crm.api.schemas.tariffs.CreateTariffPlanRequest
import org.athletica.crm.api.schemas.tariffs.TariffPlanListRequest
import org.athletica.crm.api.schemas.tariffs.TariffPlanListResponse
import org.athletica.crm.api.schemas.tariffs.UpdateTariffPlanRequest

/**
 * Клиент API тарифных планов абонементов.
 */
class TariffsApiClient(private val http: HttpClient) {
    /** Возвращает список тарифных планов организации по фильтру [request]. */
    suspend fun list(request: TariffPlanListRequest = TariffPlanListRequest()): Either<ApiClientError, TariffPlanListResponse> =
        requestCatching {
            http.post("/api/tariffs/list") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Создаёт тарифный план по данным [request]. */
    suspend fun create(request: CreateTariffPlanRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/tariffs/create") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Изменяет тарифный план по данным [request]. */
    suspend fun update(request: UpdateTariffPlanRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/tariffs/update") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Архивирует или восстанавливает тарифный план по данным [request]. */
    suspend fun archive(request: ArchiveTariffPlanRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/tariffs/archive") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
}
