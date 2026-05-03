package org.athletica.crm.api.client

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.athletica.crm.api.schemas.groups.EditGroupRequest
import org.athletica.crm.api.schemas.groups.GroupCreateRequest
import org.athletica.crm.api.schemas.groups.GroupDetailResponse
import org.athletica.crm.api.schemas.groups.GroupListRequest
import org.athletica.crm.api.schemas.groups.GroupListResponse
import org.athletica.crm.api.schemas.groups.GroupSelectItem
import org.athletica.crm.api.schemas.groups.SetGroupDisciplinesRequest
import org.athletica.crm.api.schemas.groups.SetGroupEmployeesRequest
import org.athletica.crm.core.entityids.GroupId

class GroupsApiClient(private val http: HttpClient) {
    /** Возвращает список групп организации по параметрам [request]. */
    suspend fun list(request: GroupListRequest): Either<ApiClientError, GroupListResponse> = requestCatching { http.get("/api/groups/list") }

    /** Возвращает полные данные группы по ID. */
    suspend fun detail(id: GroupId): Either<ApiClientError, GroupDetailResponse> = requestCatching { http.get("/api/groups/detail?id=$id") }

    /** Возвращает минимальный список групп организации (только id и name) для использования в селекторах. */
    suspend fun listForSelect(): Either<ApiClientError, List<GroupSelectItem>> = requestCatching { http.get("/api/groups/list-for-select") }

    /** Создаёт новую группу по данным [request]. Возвращает созданную группу. */
    suspend fun create(request: GroupCreateRequest): Either<ApiClientError, GroupDetailResponse> =
        requestCatching {
            http.post("/api/groups/create") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Редактирует существующую группу по данным [request]. Возвращает обновленную группу. */
    suspend fun edit(request: EditGroupRequest): Either<ApiClientError, GroupDetailResponse> =
        requestCatching {
            http.post("/api/groups/edit") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Устанавливает список дисциплин для группы по данным [request]. Полностью заменяет текущий список. */
    suspend fun setDisciplines(request: SetGroupDisciplinesRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/groups/set-disciplines") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Устанавливает список преподавателей группы по данным [request]. Полностью заменяет текущий список. */
    suspend fun setEmployees(request: SetGroupEmployeesRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/groups/set-employees") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
}
