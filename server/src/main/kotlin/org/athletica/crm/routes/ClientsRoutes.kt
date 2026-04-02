package org.athletica.crm.routes

import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import org.athletica.crm.api.schemas.clients.ClientListRequest
import org.athletica.crm.api.schemas.clients.ClientListResponse
import org.athletica.crm.api.schemas.clients.CreateClientRequest
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import org.athletica.crm.usecases.clients.clientDetail
import org.athletica.crm.usecases.clients.clientList
import org.athletica.crm.usecases.clients.createClient
import kotlin.uuid.Uuid

/**
 * Регистрирует маршруты для работы с клиентами:
 * GET /clients/list, GET /clients/detail, POST /clients/create.
 * Требует контекстного параметра [Database].
 */
context(db: Database)
fun Route.clientsRoutes() {
    getWithContext("/clients/list") {
        call.eitherToResponse {
            val clients = clientList(ClientListRequest()).bind()
            ClientListResponse(clients, clients.size.toUInt())
        }
    }

    getWithContext("/clients/detail") {
        call.eitherToResponse {
            val idParam =
                call.request.queryParameters["id"]
                    ?: raise(CommonDomainError("MISSING_PARAMETER", "Параметр id обязателен"))
            val id =
                runCatching { Uuid.parse(idParam) }.getOrElse {
                    raise(CommonDomainError("INVALID_PARAMETER", "Параметр id должен быть корректным UUID"))
                }
            clientDetail(id).bind()
        }
    }

    postWithContext("/clients/create") {
        call.eitherToResponse {
            val request = call.receive<CreateClientRequest>()
            createClient(request).bind()
        }
    }
}
