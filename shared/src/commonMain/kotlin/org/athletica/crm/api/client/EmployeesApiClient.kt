package org.athletica.crm.api.client

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.athletica.crm.api.schemas.employees.CreateEmployeeRequest
import org.athletica.crm.api.schemas.employees.CreateRoleRequest
import org.athletica.crm.api.schemas.employees.EmployeeDetailResponse
import org.athletica.crm.api.schemas.employees.EmployeeListItem
import org.athletica.crm.api.schemas.employees.EmployeeListResponse
import org.athletica.crm.api.schemas.employees.RoleItem
import org.athletica.crm.api.schemas.employees.RoleListResponse
import org.athletica.crm.api.schemas.employees.SendEmployeeAccessRequest
import org.athletica.crm.api.schemas.employees.UpdateEmployeeRequest
import org.athletica.crm.api.schemas.employees.UpdateRoleRequest
import org.athletica.crm.core.entityids.EmployeeId

class EmployeesApiClient(private val http: HttpClient) {
    /** Возвращает список сотрудников организации. */
    suspend fun list(): Either<ApiClientError, EmployeeListResponse> = requestCatching { http.get("/api/employees/list") }

    /** Возвращает полные данные сотрудника по ID. */
    suspend fun detail(id: EmployeeId): Either<ApiClientError, EmployeeDetailResponse> = requestCatching { http.get("/api/employees/detail?id=$id") }

    /** Создаёт нового сотрудника по данным [request]. Возвращает созданного сотрудника. */
    suspend fun create(request: CreateEmployeeRequest): Either<ApiClientError, EmployeeListItem> =
        requestCatching {
            http.post("/api/employees/create") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Обновляет данные сотрудника по [request]. */
    suspend fun update(request: UpdateEmployeeRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/employees/update") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Отправляет сотруднику доступ: устанавливает пароль, активирует аккаунт, высылает email. */
    suspend fun sendAccess(request: SendEmployeeAccessRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/employees/send-access") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Возвращает список ролей организации. */
    suspend fun roles(): Either<ApiClientError, RoleListResponse> = requestCatching { http.get("/api/employees/roles") }

    /** Создаёт новую роль по данным [request]. Возвращает созданную роль. */
    suspend fun createRole(request: CreateRoleRequest): Either<ApiClientError, RoleItem> =
        requestCatching {
            http.post("/api/employees/roles/create") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Обновляет существующую роль по данным [request]. Возвращает обновлённую роль. */
    suspend fun updateRole(request: UpdateRoleRequest): Either<ApiClientError, RoleItem> =
        requestCatching {
            http.post("/api/employees/roles/update") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
}
