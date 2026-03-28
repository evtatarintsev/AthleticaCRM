package org.athletica.crm.routes

import arrow.core.raise.either
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.athletica.crm.api.schemas.AuthMeResponse
import org.athletica.crm.db.Database
import org.athletica.crm.usecases.profile

context(db: Database)
fun Route.profileRoutes() {
    getWithContext("/auth/me") {
        either {
            val user = profile().bind()
            AuthMeResponse(
                id = user.id.toString(),
                username = user.username,
            )
        }.fold(
            { call.respondWithError(it) },
            { call.respond(it) },
        )
    }
}
