package org.athletica.crm.api.client

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import org.athletica.crm.api.schemas.AuthMeResponse
import org.athletica.crm.api.schemas.ErrorResponse
import org.athletica.crm.api.schemas.auth.LoginRequest
import org.athletica.crm.api.schemas.auth.LoginResponse
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.api.schemas.clients.ClientDetailResponse
import org.athletica.crm.api.schemas.clients.ClientListRequest
import org.athletica.crm.api.schemas.clients.ClientListResponse
import org.athletica.crm.api.schemas.clients.CreateClientRequest
import org.athletica.crm.api.schemas.groups.GroupCreateRequest
import org.athletica.crm.api.schemas.groups.GroupDetailResponse
import org.athletica.crm.api.schemas.groups.GroupListRequest
import org.athletica.crm.api.schemas.groups.GroupListResponse
import org.athletica.crm.api.schemas.sports.CreateSportRequest
import org.athletica.crm.api.schemas.sports.DeleteSportRequest
import org.athletica.crm.api.schemas.sports.SportDetailResponse
import org.athletica.crm.api.schemas.sports.SportListResponse
import org.athletica.crm.api.schemas.sports.UpdateSportRequest
import org.athletica.crm.api.schemas.upload.UploadResponse
import kotlin.uuid.Uuid
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders

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

    /** Возвращает страницу клиентов организации по параметрам [request]. */
    suspend fun clientList(request: ClientListRequest): Either<ApiClientError, ClientListResponse> = execute { http.get("/api/clients/list") }

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

    /** Возвращает список видов спорта организации. */
    suspend fun sportList(): Either<ApiClientError, SportListResponse> = execute { http.get("/api/sports/list") }

    /** Создаёт новый вид спорта по данным [request]. Возвращает созданный вид спорта. */
    suspend fun createSport(request: CreateSportRequest): Either<ApiClientError, SportDetailResponse> =
        execute {
            http.post("/api/sports/create") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Обновляет название вида спорта по данным [request]. Возвращает обновлённый вид спорта. */
    suspend fun updateSport(request: UpdateSportRequest): Either<ApiClientError, SportDetailResponse> =
        execute {
            http.post("/api/sports/update") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Возвращает информацию о загрузке по [id], включая presigned URL аватара. */
    suspend fun uploadInfo(id: Uuid): Either<ApiClientError, UploadResponse> =
        execute { http.get("/api/upload/info") { url { parameters.append("id", id.toString()) } } }

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

    /** Удаляет виды спорта по списку id из [request]. Атомарная операция. */
    suspend fun deleteSport(request: DeleteSportRequest): Either<ApiClientError, Unit> =
        execute {
            http.post("/api/sports/delete") {
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
