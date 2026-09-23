package org.athletica.crm.components.schedule

import arrow.core.Either
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.schedule.ScheduleListRequest
import org.athletica.crm.api.schemas.schedule.ScheduleListResponse

/** Источник данных страницы расписания: занятия периода и справочники фильтров. */
interface ScheduleSource {
    /** Занятия периода и фильтров из [request]. */
    suspend fun sessions(request: ScheduleListRequest): Either<ApiClientError, ScheduleListResponse>

    /** Справочники залов, дисциплин и сотрудников; недоступный справочник остаётся пустым. */
    suspend fun dictionaries(): ScheduleDictionaries
}

/** [ScheduleSource] поверх API сервера. */
class ApiScheduleSource(private val api: ApiClient) : ScheduleSource {
    override suspend fun sessions(request: ScheduleListRequest): Either<ApiClientError, ScheduleListResponse> = api.schedule.list(request)

    override suspend fun dictionaries(): ScheduleDictionaries =
        coroutineScope {
            val halls = async { api.halls.list() }
            val disciplines = async { api.disciplines.list() }
            val employees = async { api.employees.list() }
            ScheduleDictionaries(
                halls = halls.await().fold({ emptyList() }, { it.halls }),
                disciplines = disciplines.await().fold({ emptyList() }, { it.disciplines }),
                employees = employees.await().fold({ emptyList() }, { it.employees }),
            )
        }
}
