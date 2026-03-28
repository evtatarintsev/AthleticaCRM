package org.athletica.crm.routes

import arrow.core.raise.either
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.athletica.crm.api.schemas.clients.ClientListRequest
import org.athletica.crm.api.schemas.clients.ClientListResponse
import org.athletica.crm.api.schemas.clients.CreateClientRequest
import org.athletica.crm.db.Database
import org.athletica.crm.usecases.clients.clientList
import org.athletica.crm.usecases.clients.createClient
import org.athletica.crm.usecases.clients.totalClientsCount

context(db: Database)
fun Route.clientsRoutes() {
    getWithContext("/clients/list") {
        either {
            val clients = clientList(ClientListRequest()).bind()
            val totalCount = totalClientsCount(ClientListRequest()).bind()
            ClientListResponse(clients, totalCount)
        }.fold(
            { call.respondWithError(it) },
            { call.respond(it) },
        )
    }

    postWithContext("/clients/create") {
        either {
            val request = call.receive<CreateClientRequest>()
            createClient(request).bind()
        }.fold(
            { call.respondWithError(it) },
            { call.respond(it) },
        )
    }
}
