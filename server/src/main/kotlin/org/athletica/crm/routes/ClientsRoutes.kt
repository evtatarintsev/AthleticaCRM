package org.athletica.crm.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.routing.post
import org.athletica.crm.api.schemas.clients.AddClientsToGroupRequest
import org.athletica.crm.api.schemas.clients.AdjustBalanceRequest
import org.athletica.crm.api.schemas.clients.AttachClientDocRequest
import org.athletica.crm.api.schemas.clients.BalanceJournalEntry
import org.athletica.crm.api.schemas.clients.ClientBalanceHistoryRequest
import org.athletica.crm.api.schemas.clients.ClientBalanceHistoryResponse
import org.athletica.crm.api.schemas.clients.ClientDetailRequest
import org.athletica.crm.api.schemas.clients.ClientDetailResponse
import org.athletica.crm.api.schemas.clients.ClientDoc
import org.athletica.crm.api.schemas.clients.ClientGroup
import org.athletica.crm.api.schemas.clients.ClientListItem
import org.athletica.crm.api.schemas.clients.ClientListRequest
import org.athletica.crm.api.schemas.clients.ClientListResponse
import org.athletica.crm.api.schemas.clients.CreateClientRequest
import org.athletica.crm.api.schemas.clients.DeleteClientDocRequest
import org.athletica.crm.api.schemas.clients.EditClientRequest
import org.athletica.crm.api.schemas.clients.RemoveClientFromGroupRequest
import org.athletica.crm.api.schemas.customfields.CustomFieldValues
import org.athletica.crm.core.Gender
import org.athletica.crm.domain.clientbalance.ClientBalance
import org.athletica.crm.domain.clientbalance.ClientBalanceEntry
import org.athletica.crm.domain.clientbalance.ClientBalances
import org.athletica.crm.domain.clients.Client
import org.athletica.crm.domain.clients.Clients
import org.athletica.crm.domain.clients.clientDoc
import org.athletica.crm.domain.customfields.CustomFieldDefinitions
import org.athletica.crm.domain.enrollments.Enrollments
import org.athletica.crm.storage.Database
import org.athletica.crm.usecases.customfields.getCustomFields

/**
 * Регистрирует маршруты для работы с клиентами:
 * Требует контекстных параметров [Database] и [AuditLog].
 */
context(db: Database)
fun RouteWithContext.clientsRoutes(
    clients: Clients,
    balances: ClientBalances,
    enrollments: Enrollments,
    definitions: CustomFieldDefinitions,
) {
    get<Unit, ClientListResponse>("/clients/list") {
        val clientList =
            db.transaction {
                clients.list()
            }
        ClientListResponse(clientList.map { it.toListItem() }, clientList.size.toUInt())
    }

    post<ClientListRequest, ByteArray>("/clients/export") { request, call ->
        val format = call.request.queryParameters["format"] ?: "csv"

        val clientList =
            db.transaction {
                clients.list()
            }

        // Generate CSV content
        val csvContent = generateCsvContent(clientList.map { it.toListItem() })

        // Set appropriate headers for download
        call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=\"clients.${format}\"")
        call.response.headers.append(HttpHeaders.ContentType, if (format == "xlsx") "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" else "text/csv; charset=UTF-8")

        csvContent
    }

    get<ClientDetailRequest, ClientDetailResponse>("/clients/detail") { request ->
        db.transaction {
            clients.byId(request.id).detailResponse()
        }
    }

    post<CreateClientRequest, ClientDetailResponse>("/clients/create") { request ->
        db.transaction {
            val defs = getCustomFields(definitions, CLIENT_ENTITY_TYPE)
            val customFields = CustomFieldValues(defs).with(request.customFields).bind()
            clients
                .new(
                    request.id,
                    request.name,
                    request.avatarId,
                    request.birthday,
                    request.gender,
                    request.leadSourceId,
                    customFields.toList(),
                )
        }.detailResponse()
    }

    post<EditClientRequest, ClientDetailResponse>("/clients/edit") { request ->
        db.transaction {
            val defs = getCustomFields(definitions, CLIENT_ENTITY_TYPE)
            val customFields = CustomFieldValues(defs).with(request.customFields).bind()
            clients
                .byId(request.id)
                .withNew(
                    request.name,
                    request.avatarId,
                    request.birthday,
                    request.gender,
                    request.leadSourceId,
                    customFields.toList(),
                )
                .apply { save() }
        }.detailResponse()
    }

    post<AddClientsToGroupRequest, Unit>("/clients/add-to-group") { request ->
        db.transaction {
            enrollments.add(request.groupId, request.clientIds)
        }
    }

    post<RemoveClientFromGroupRequest, Unit>("/clients/remove-from-group") { request ->
        db.transaction {
            enrollments.remove(request.groupId, request.clientIds)
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

    get<ClientBalanceHistoryRequest, ClientBalanceHistoryResponse>("/clients/balance/history") { request ->
        db.transaction {
            balances.forClient(request.id).historyResponse()
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
        leadSourceId = leadSourceId,
        customFields = customFields,
    )

fun Client.toListItem() =
    ClientListItem(
        id = id,
        name = name,
        avatarId = avatarId,
        birthday = birthday,
        gender = gender,
        groups = groups.map { org.athletica.crm.api.schemas.clients.ClientGroup(it.id, it.name) },
        balance = balance,
        customFields = customFields,
    )

private const val CLIENT_ENTITY_TYPE = "CLIENT"

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

/**
 * Генерирует CSV контент для экспорта списка клиентов.
 * [clients] — список клиентов для экспорта.
 * Возвращает ByteArray с CSV данными в кодировке UTF-8.
 */
private fun generateCsvContent(clients: List<ClientListItem>): ByteArray {
    val sb = StringBuilder()

    // Заголовок CSV
    sb.appendLine("ID,Имя,Дата рождения,Пол,Группы,Баланс")

    // Данные клиентов
    clients.forEach { client ->
        val groupsStr = client.groups.joinToString("; ") { it.name }
        val birthdayStr = client.birthday?.toString() ?: ""
        val genderStr =
            when (client.gender) {
                org.athletica.crm.core.Gender.MALE -> "М"
                org.athletica.crm.core.Gender.FEMALE -> "Ж"
            }
        sb.appendLine("${client.id},${client.name},$birthdayStr,$genderStr,$groupsStr,${client.balance}")
    }

    return sb.toString().toByteArray(Charsets.UTF_8)
}
