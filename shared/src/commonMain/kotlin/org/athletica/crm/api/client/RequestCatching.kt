package org.athletica.crm.api.client

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import org.athletica.crm.api.schemas.ErrorResponse

/**
 *
 */
suspend inline fun <reified T> requestCatching(noinline request: suspend () -> HttpResponse): Either<ApiClientError, T> {
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
