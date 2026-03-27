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
import org.athletica.crm.api.schemas.LoginRequest
import org.athletica.crm.api.schemas.LoginResponse
import org.athletica.crm.api.schemas.SignUpRequest

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
