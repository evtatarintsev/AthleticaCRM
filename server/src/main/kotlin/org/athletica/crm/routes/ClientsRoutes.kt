package org.athletica.crm.routes

import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import org.athletica.crm.api.schemas.clients.AddClientsToGroupRequest
import org.athletica.crm.api.schemas.clients.AdjustBalanceRequest
import org.athletica.crm.api.schemas.clients.AttachClientDocRequest
import org.athletica.crm.api.schemas.clients.ClientListRequest
import org.athletica.crm.api.schemas.clients.ClientListResponse
import org.athletica.crm.api.schemas.clients.CreateClientRequest
import org.athletica.crm.api.schemas.clients.RemoveClientFromGroupRequest
import org.athletica.crm.audit.AuditLog
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import org.athletica.crm.i18n.Messages
import org.athletica.crm.usecases.clients.addClientsToGroup
import org.athletica.crm.usecases.clients.adjustClientBalance
import org.athletica.crm.usecases.clients.attachClientDoc
import org.athletica.crm.usecases.clients.clientBalanceHistory
import org.athletica.crm.usecases.clients.clientDetail
import org.athletica.crm.usecases.clients.clientList
import org.athletica.crm.usecases.clients.createClient
import org.athletica.crm.usecases.clients.removeClientsFromGroup
import kotlin.uuid.Uuid

/**
 * Регистрирует маршруты для работы с клиентами:
 * Требует контекстных параметров [Database] и [AuditLog].
 */
context(db: Database, audit: AuditLog)
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
                    ?: raise(CommonDomainError("MISSING_PARAMETER", Messages.MissingParameterId.localize()))
            val id =
                runCatching { Uuid.parse(idParam) }.getOrElse {
                    raise(CommonDomainError("INVALID_PARAMETER", Messages.InvalidParameterId.localize()))
                }
            clientDetail(id).bind()
        }
    }

    postWithContext("/clients/create") {
        call.eitherToResponse {
            val request = call.receive<CreateClientRequest>()
            val result = createClient(request).bind()
            result
        }
    }

    postWithContext("/clients/add-to-group") {
        call.eitherToResponse {
            val request = call.receive<AddClientsToGroupRequest>()
            addClientsToGroup(request).bind()
        }
    }

    postWithContext("/clients/remove-from-group") {
        call.eitherToResponse {
            val request = call.receive<RemoveClientFromGroupRequest>()
            removeClientsFromGroup(request).bind()
        }
    }

    postWithContext("/clients/balance/adjust") {
        call.eitherToResponse {
            val request = call.receive<AdjustBalanceRequest>()
            adjustClientBalance(request).bind()
        }
    }

    postWithContext("/clients/docs/attach") {
        call.eitherToResponse {
            val request = call.receive<AttachClientDocRequest>()
            attachClientDoc(request).bind()
        }
    }

    getWithContext("/clients/balance/history") {
        call.eitherToResponse {
            val idParam =
                call.request.queryParameters["id"]
                    ?: raise(CommonDomainError("MISSING_PARAMETER", Messages.MissingParameterId.localize()))
            val id =
                runCatching { Uuid.parse(idParam) }.getOrElse {
                    raise(CommonDomainError("INVALID_PARAMETER", Messages.InvalidParameterId.localize()))
                }
            clientBalanceHistory(id).bind()
        }
    }
}
