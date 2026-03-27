package org.athletica.crm.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingCall
import org.athletica.crm.api.schemas.ErrorResponse
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.security.UserNotFound
import org.athletica.crm.usecases.SignUpError

private val DomainError.httpStatus: HttpStatusCode
    get() =
        when (this) {
            is UserNotFound -> HttpStatusCode.Unauthorized
            is SignUpError.UserAlreadyRegistered -> HttpStatusCode.Conflict
            else -> HttpStatusCode.BadRequest
        }

/** Сериализует [error] в [ErrorResponse] и отвечает соответствующим HTTP-статусом. */
suspend fun RoutingCall.respondWithError(error: DomainError) =
    respond(
        error.httpStatus,
        ErrorResponse(code = error.code, message = error.message),
    )
