package org.athletica.crm.routes

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import io.ktor.client.request.request
import io.ktor.http.Parameters
import io.ktor.server.routing.post
import org.athletica.crm.api.schemas.clients.AddClientsToGroupRequest
import org.athletica.crm.api.schemas.clients.AdjustBalanceRequest
import org.athletica.crm.api.schemas.clients.AttachClientDocRequest
import org.athletica.crm.api.schemas.clients.BalanceJournalEntry
import org.athletica.crm.api.schemas.clients.ClientBalanceHistoryResponse
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
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.clientbalance.ClientBalance
import org.athletica.crm.domain.clientbalance.ClientBalanceEntry
import org.athletica.crm.domain.clientbalance.ClientBalances
import org.athletica.crm.domain.clients.Client
import org.athletica.crm.domain.clients.Clients
import org.athletica.crm.domain.clients.clientDoc
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Database
import org.athletica.crm.usecases.clients.addClientsToGroup
import org.athletica.crm.usecases.clients.clientList
import org.athletica.crm.usecases.clients.removeClientsFromGroup
import kotlin.uuid.Uuid

/**
 * Регистрирует маршруты для работы с клиентами:
 * Требует контекстных параметров [Database] и [AuditLog].
 */
context(db: Database, audit: AuditLog)
fun RouteWithContext.clientsRoutes(clients: Clients, balances: ClientBalances) {
    get("/clients/list") {
        call.eitherToResponse {
            val clients = clientList(ClientListRequest()).bind()
            ClientListResponse(clients, clients.size.toUInt())
        }
    }

    get("/clients/detail") {
        call.eitherToResponse {
            val id = call.request.queryParameters.asUuid("id").toClientId()
            db.transaction {
                clients.byId(id).detailResponse()
            }
        }
    }

    post<CreateClientRequest, ClientDetailResponse>("/clients/create") { request ->
        db.transaction {
            clients
                .new(
                    request.id,
                    request.name,
                    request.avatarId,
                    request.birthday,
                    request.gender,
                )
        }.detailResponse()
    }

    post<EditClientRequest, ClientDetailResponse>("/clients/edit") { request ->
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
        }.detailResponse()
    }

    post<AddClientsToGroupRequest, Unit>("/clients/add-to-group") { request ->
        db.transaction {
            addClientsToGroup(request)
        }
    }

    post<RemoveClientFromGroupRequest, Unit>("/clients/remove-from-group") { request ->
        db.transaction {
            removeClientsFromGroup(request)
        }
    }

    post<AdjustBalanceRequest, ClientDetailResponse>("/clients/balance/adjust") { request ->
        db.transaction {
            balances
                .forClient(request.clientId)
                .adjust(request.amount, request.note)
            clients.byId(request.clientId).detailResponse()
        }
    }

    post<AttachClientDocRequest, Unit>("/clients/docs/attach") { request ->
        db.transaction {
            clients
                .byId(request.clientId)
                .attachDoc(clientDoc(request.uploadId, request.name))
                .save()
        }
    }

    post<DeleteClientDocRequest, Unit>("/clients/docs/delete") { request ->
        db.transaction {
            clients
                .byId(request.clientId)
                .deleteDoc(request.docId)
                .save()
        }
    }

    get("/clients/balance/history") {
        call.eitherToResponse {
            val id = call.request.queryParameters.asUuid("id").toClientId()
            db.transaction {
                balances.forClient(id).historyResponse()
            }
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

fun ClientBalance.historyResponse() =
    ClientBalanceHistoryResponse(
        entries = history.map { it.toJournalEntry() },
    )

private fun ClientBalanceEntry.toJournalEntry() =
    BalanceJournalEntry(
        id = id,
        amount = amount,
        balanceAfter = balanceAfter,
        operationType = operationType,
        note = note,
        performedBy = performedBy,
        createdAt = createdAt,
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
