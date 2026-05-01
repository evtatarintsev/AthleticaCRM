package org.athletica.crm.api.client

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.athletica.crm.api.schemas.auth.LoginRequest
import org.athletica.crm.api.schemas.auth.LoginResponse
import org.athletica.crm.api.schemas.auth.SignUpRequest

class AuthApiClient(private val http: HttpClient) {
    /** Выполняет вход по данным [request]. Возвращает access и refresh токены. */
    suspend fun login(request: LoginRequest): Either<ApiClientError, LoginResponse> =
        requestCatching {
            http.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Регистрирует нового пользователя по данным [request]. Возвращает access и refresh токены. */
    suspend fun signUp(request: SignUpRequest): Either<ApiClientError, LoginResponse> =
        requestCatching {
            http.post("/api/auth/sign-up") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Завершает сессию текущего пользователя на сервере. */
    suspend fun logout(): Either<ApiClientError, Unit> = requestCatching { http.post("/api/auth/logout") }
}
