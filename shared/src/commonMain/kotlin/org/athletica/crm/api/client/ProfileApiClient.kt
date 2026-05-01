package org.athletica.crm.api.client

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.athletica.crm.api.schemas.AuthMeResponse
import org.athletica.crm.api.schemas.ChangePasswordRequest
import org.athletica.crm.api.schemas.UpdateMeRequest
import org.athletica.crm.api.schemas.auth.AuthBranchesRequest
import org.athletica.crm.api.schemas.auth.AuthBranchesResponse
import org.athletica.crm.api.schemas.auth.LoginResponse
import org.athletica.crm.api.schemas.auth.SwitchBranchRequest

class ProfileApiClient(private val http: HttpClient) {
    /** Возвращает данные текущего авторизованного пользователя. */
    suspend fun me(): Either<ApiClientError, AuthMeResponse> = requestCatching { http.get("/api/auth/me") }

    /** Возвращает список филиалов, доступных текущему аутентифицированному пользователю. */
    suspend fun myBranches(): Either<ApiClientError, AuthBranchesResponse> = requestCatching { http.get("/api/auth/my-branches") }

    /** Переключает активный филиал текущего пользователя. Возвращает новые токены. */
    suspend fun switchBranch(request: SwitchBranchRequest): Either<ApiClientError, LoginResponse> =
        requestCatching {
            http.post("/api/auth/switch-branch") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Обновляет имя и аватар текущего авторизованного пользователя. Возвращает обновлённый профиль. */
    suspend fun update(request: UpdateMeRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/auth/me/update") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Меняет пароль текущего авторизованного пользователя. */
    suspend fun changePassword(request: ChangePasswordRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/auth/me/change-password") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Возвращает список доступных филиалов для учётных данных из [request]. */
    suspend fun branches(request: AuthBranchesRequest): Either<ApiClientError, AuthBranchesResponse> =
        requestCatching {
            http.post("/api/auth/branches") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
}
