package org.athletica.crm.api.client

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import org.athletica.crm.api.schemas.AuthMeResponse
import org.athletica.crm.api.schemas.ChangePasswordRequest
import org.athletica.crm.api.schemas.ErrorResponse
import org.athletica.crm.api.schemas.UpdateMeRequest
import org.athletica.crm.api.schemas.audit.AuditLogListResponse
import org.athletica.crm.api.schemas.auth.LoginRequest
import org.athletica.crm.api.schemas.auth.LoginResponse
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.api.schemas.clients.ClientDetailResponse
import org.athletica.crm.api.schemas.clients.ClientListRequest
import org.athletica.crm.api.schemas.clients.ClientListResponse
import org.athletica.crm.api.schemas.clients.CreateClientRequest
import org.athletica.crm.api.schemas.disciplines.CreateDisciplineRequest
import org.athletica.crm.api.schemas.disciplines.DeleteDisciplineRequest
import org.athletica.crm.api.schemas.disciplines.DisciplineDetailResponse
import org.athletica.crm.api.schemas.disciplines.DisciplineListResponse
import org.athletica.crm.api.schemas.disciplines.UpdateDisciplineRequest
import org.athletica.crm.api.schemas.groups.GroupCreateRequest
import org.athletica.crm.api.schemas.groups.GroupDetailResponse
import org.athletica.crm.api.schemas.groups.GroupListRequest
import org.athletica.crm.api.schemas.groups.GroupListResponse
import org.athletica.crm.api.schemas.org.OrgSettingsResponse
import org.athletica.crm.api.schemas.org.UpdateOrgSettingsRequest
import org.athletica.crm.api.schemas.upload.UploadResponse
import kotlin.uuid.Uuid

/**
 * Клиент для взаимодействия с API сервера.
 * Принимает настроенный [http] — Ktor HTTP клиент с аутентификацией и сериализацией.
 */
class ApiClient(private val http: HttpClient) {
    /** Выполняет вход по данным [request]. Возвращает access и refresh токены. */
    suspend fun login(request: LoginRequest): Either<ApiClientError, LoginResponse> =
        execute {
            http.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Регистрирует нового пользователя по данным [request]. Возвращает access и refresh токены. */
    suspend fun signUp(request: SignUpRequest): Either<ApiClientError, LoginResponse> =
        execute {
            http.post("/api/auth/sign-up") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Завершает сессию текущего пользователя на сервере. */
    suspend fun logout(): Either<ApiClientError, Unit> = execute { http.post("/api/auth/logout") }

    /** Возвращает данные текущего авторизованного пользователя. */
    suspend fun me(): Either<ApiClientError, AuthMeResponse> = execute { http.get("/api/auth/me") }

    /** Обновляет имя и аватар текущего авторизованного пользователя. Возвращает обновлённый профиль. */
    suspend fun updateMe(request: UpdateMeRequest): Either<ApiClientError, AuthMeResponse> =
        execute {
            http.post("/api/auth/me/update") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Меняет пароль текущего авторизованного пользователя. */
    suspend fun changePassword(request: ChangePasswordRequest): Either<ApiClientError, Unit> =
        execute {
            http.post("/api/auth/me/change-password") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Возвращает страницу клиентов организации по параметрам [request]. */
    suspend fun clientList(request: ClientListRequest): Either<ApiClientError, ClientListResponse> = execute { http.get("/api/clients/list") }

    /** Возвращает полные данные клиента по [id]. */
    suspend fun clientDetail(id: Uuid): Either<ApiClientError, ClientDetailResponse> = execute { http.get("/api/clients/detail") { url { parameters.append("id", id.toString()) } } }

    /** Возвращает список групп организации по параметрам [request]. */
    suspend fun groupList(request: GroupListRequest): Either<ApiClientError, GroupListResponse> = execute { http.get("/api/groups/list") }

    /** Создаёт новую группу по данным [request]. Возвращает созданную группу. */
    suspend fun createGroup(request: GroupCreateRequest): Either<ApiClientError, GroupDetailResponse> =
        execute {
            http.post("/api/groups/create") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Создаёт нового клиента по данным [request]. Возвращает созданного клиента. */
    suspend fun createClient(request: CreateClientRequest): Either<ApiClientError, ClientDetailResponse> =
        execute {
            http.post("/api/clients/create") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Возвращает основные настройки организации (название, часовой пояс). */
    suspend fun orgSettings(): Either<ApiClientError, OrgSettingsResponse> = execute { http.get("/api/org/settings") }

    /** Обновляет название и часовой пояс организации по данным [request]. */
    suspend fun updateOrgSettings(request: UpdateOrgSettingsRequest): Either<ApiClientError, OrgSettingsResponse> =
        execute {
            http.post("/api/org/settings/update") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Возвращает список дисциплин организации. */
    suspend fun disciplineList(): Either<ApiClientError, DisciplineListResponse> = execute { http.get("/api/disciplines/list") }

    /** Создаёт новую дисциплину по данным [request]. Возвращает созданную дисциплину. */
    suspend fun createDiscipline(request: CreateDisciplineRequest): Either<ApiClientError, DisciplineDetailResponse> =
        execute {
            http.post("/api/disciplines/create") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Обновляет название дисциплины по данным [request]. Возвращает обновлённую дисциплину. */
    suspend fun updateDiscipline(request: UpdateDisciplineRequest): Either<ApiClientError, DisciplineDetailResponse> =
        execute {
            http.post("/api/disciplines/update") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Возвращает информацию о загрузке по [id], включая presigned URL аватара. */
    suspend fun uploadInfo(id: Uuid): Either<ApiClientError, UploadResponse> = execute { http.get("/api/upload/info") { url { parameters.append("id", id.toString()) } } }

    /** Загружает файл на сервер. Возвращает [UploadResponse] с id и presigned URL. */
    suspend fun uploadFile(
        bytes: ByteArray,
        filename: String,
        contentType: String,
    ): Either<ApiClientError, UploadResponse> =
        execute {
            http.post("/api/upload") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append(
                                "file",
                                bytes,
                                Headers.build {
                                    append(HttpHeaders.ContentType, contentType)
                                    append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                                },
                            )
                        },
                    ),
                )
            }
        }

    /** Возвращает страницу лога аудита с пагинацией и фильтрами. */
    suspend fun auditLogList(
        page: Int = 0,
        pageSize: Int = 50,
        actionType: String? = null,
        userId: String? = null,
        entityType: String? = null,
        from: String? = null,
        to: String? = null,
    ): Either<ApiClientError, AuditLogListResponse> =
        execute {
            http.get("/api/audit/log") {
                url {
                    parameters.append("page", page.toString())
                    parameters.append("pageSize", pageSize.toString())
                    if (actionType != null) parameters.append("actionType", actionType)
                    if (userId != null) parameters.append("userId", userId)
                    if (entityType != null) parameters.append("entityType", entityType)
                    if (from != null) parameters.append("from", from)
                    if (to != null) parameters.append("to", to)
                }
            }
        }

    /** Удаляет дисциплины по списку id из [request]. Атомарная операция. */
    suspend fun deleteDiscipline(request: DeleteDisciplineRequest): Either<ApiClientError, Unit> =
        execute {
            http.post("/api/disciplines/delete") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    private suspend inline fun <reified T> execute(noinline request: suspend () -> HttpResponse): Either<ApiClientError, T> {
        val response =
            try {
                request()
            } catch (e: ConnectTimeoutException) {
                return ApiClientError.Unavailable(e).left()
            } catch (e: SocketTimeoutException) {
                return ApiClientError.Unavailable(e).left()
            } catch (e: HttpRequestTimeoutException) {
                return ApiClientError.Unavailable(e).left()
            } catch (e: Exception) {
                return ApiClientError.Unavailable(e).left()
            }

        return if (response.status.isSuccess()) {
            try {
                response.body<T>().right()
            } catch (e: Exception) {
                ApiClientError.Unavailable(e).left()
            }
        } else if (response.status == HttpStatusCode.Unauthorized) {
            ApiClientError.Unauthenticated.left()
        } else {
            try {
                val error = response.body<ErrorResponse>()
                ApiClientError.ValidationError(error.code, error.message, error.fields).left()
            } catch (e: Exception) {
                ApiClientError.Unavailable(e).left()
            }
        }
    }
}
