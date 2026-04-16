package org.athletica.crm.routes

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import io.ktor.http.Parameters
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import org.athletica.crm.api.schemas.clients.AddClientsToGroupRequest
import org.athletica.crm.api.schemas.clients.AdjustBalanceRequest
import org.athletica.crm.api.schemas.clients.AttachClientDocRequest
import org.athletica.crm.api.schemas.clients.ClientDetailResponse
import org.athletica.crm.api.schemas.clients.ClientDoc
import org.athletica.crm.api.schemas.clients.ClientGroup
import org.athletica.crm.api.schemas.clients.ClientListRequest
import org.athletica.crm.api.schemas.clients.ClientListResponse
import org.athletica.crm.api.schemas.clients.CreateClientRequest
import org.athletica.crm.api.schemas.clients.DeleteClientDocRequest
import org.athletica.crm.api.schemas.clients.EditClientRequest
import org.athletica.crm.api.schemas.clients.RemoveClientFromGroupRequest
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.toClientId
import org.athletica.crm.db.Database
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.clients.Client
import org.athletica.crm.domain.clients.Clients
import org.athletica.crm.domain.clients.clientDoc
import org.athletica.crm.i18n.Messages
import org.athletica.crm.usecases.clients.addClientsToGroup
import org.athletica.crm.usecases.clients.adjustClientBalance
import org.athletica.crm.usecases.clients.clientBalanceHistory
import org.athletica.crm.usecases.clients.clientList
import org.athletica.crm.usecases.clients.removeClientsFromGroup
import kotlin.uuid.Uuid

/**
 * Регистрирует маршруты для работы с клиентами:
 * Требует контекстных параметров [Database] и [AuditLog].
 */
context(db: Database, audit: AuditLog)
fun Route.clientsRoutes(clients: Clients) {
    getWithContext("/clients/list") {
        call.eitherToResponse {
            val clients = clientList(ClientListRequest()).bind()
            ClientListResponse(clients, clients.size.toUInt())
        }
    }

    getWithContext("/clients/detail") {
        call.eitherToResponse {
            val id = call.request.queryParameters.asUuid("id").toClientId()
            db.transaction {
                clients.byId(id).detailResponse()
            }
        }
    }

    postWithContext("/clients/create") {
        call.eitherToResponse {
            val request = call.receive<CreateClientRequest>()
            db.transaction {
                clients
                    .new(
                        request.id,
                        request.name,
                        request.avatarId,
                        request.birthday,
                        request.gender,
                    )
                    .detailResponse()
            }
        }
    }

    postWithContext("/clients/edit") {
        call.eitherToResponse {
            val request = call.receive<EditClientRequest>()
            db.transaction {
                clients
                    .byId(request.id)
                    .withNew(
                        request.name,
                        request.avatarId,
                        request.birthday,
                        request.gender,
                    )
                    .apply { save() }
                    .detailResponse()
            }
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
            db.transaction {
                clients.byId(request.clientId)
            }
        }
    }

    postWithContext("/clients/docs/attach") {
        call.eitherToResponse {
            val request = call.receive<AttachClientDocRequest>()
            db.transaction {
                clients
                    .byId(request.clientId)
                    .attachDoc(clientDoc(request.uploadId, request.name))
                    .save()
            }
        }
    }

    postWithContext("/clients/docs/delete") {
        call.eitherToResponse {
            val request = call.receive<DeleteClientDocRequest>()
            db.transaction {
                clients
                    .byId(request.clientId)
                    .deleteDoc(request.docId)
                    .save()
            }
        }
    }

    getWithContext("/clients/balance/history") {
        call.eitherToResponse {
            val idParam =
                call.request.queryParameters["id"]
                    ?: raise(CommonDomainError("MISSING_PARAMETER", Messages.MissingParameterId.localize()))
            val id =
                runCatching { Uuid.parse(idParam).toClientId() }.getOrElse {
                    raise(CommonDomainError("INVALID_PARAMETER", Messages.InvalidParameterId.localize()))
                }
            clientBalanceHistory(id).bind()
        }
    }
}

fun Client.detailResponse() =
    ClientDetailResponse(
        id = id,
        name = name,
        avatarId = avatarId,
        birthday = birthday,
        gender = gender,
        groups = groups.map { ClientGroup(it.id, it.name) },
        balance = balance,
        docs = docs.map { ClientDoc(it.id, it.uploadId, it.name, it.createdAt) },
    )

context(ctx: RequestContext, raise: Raise<CommonDomainError>)
fun Parameters.asUuid(name: String): Uuid {
    val idParam =
        get(name)
            ?: raise(CommonDomainError("MISSING_PARAMETER", Messages.MissingParameterId.localize()))
    return runCatching { Uuid.parse(idParam) }.getOrElse {
        raise(CommonDomainError("INVALID_PARAMETER", Messages.InvalidParameterId.localize()))
    }
}
