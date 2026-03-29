package org.athletica.crm.routes

import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import org.athletica.crm.api.schemas.clients.ClientListRequest
import org.athletica.crm.api.schemas.clients.ClientListResponse
import org.athletica.crm.api.schemas.clients.CreateClientRequest
import org.athletica.crm.db.Database
import org.athletica.crm.usecases.clients.clientList
import org.athletica.crm.usecases.clients.createClient
import org.athletica.crm.usecases.clients.totalClientsCount

/**
 * Регистрирует маршруты для работы с клиентами:
 * GET /clients/list, POST /clients/create.
 * Требует контекстного параметра [Database].
 */
context(db: Database)
fun Route.clientsRoutes() {
    getWithContext("/clients/list") {
        call.eitherToResponse {
            val clients = clientList(ClientListRequest()).bind()
            val totalCount = totalClientsCount(ClientListRequest()).bind()
            ClientListResponse(clients, totalCount)
        }
    }

    postWithContext("/clients/create") {
        call.eitherToResponse {
            val request = call.receive<CreateClientRequest>()
            createClient(request).bind()
        }
    }
}
