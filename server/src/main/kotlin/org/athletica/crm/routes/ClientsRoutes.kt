package org.athletica.crm.routes

import arrow.fx.coroutines.parZip
import io.ktor.http.HttpHeaders
import org.athletica.crm.api.schemas.clients.AddClientsToGroupRequest
import org.athletica.crm.api.schemas.clients.AdjustBalanceRequest
import org.athletica.crm.api.schemas.clients.AttachClientDocRequest
import org.athletica.crm.api.schemas.clients.BalanceJournalEntry
import org.athletica.crm.api.schemas.clients.ClientBalanceHistoryRequest
import org.athletica.crm.api.schemas.clients.ClientBalanceHistoryResponse
import org.athletica.crm.api.schemas.clients.ClientDetailRequest
import org.athletica.crm.api.schemas.clients.ClientDetailResponse
import org.athletica.crm.api.schemas.clients.ClientDoc
import org.athletica.crm.api.schemas.clients.ClientExportRequest
import org.athletica.crm.api.schemas.clients.ClientField
import org.athletica.crm.api.schemas.clients.ClientGroup
import org.athletica.crm.api.schemas.clients.ClientListItem
import org.athletica.crm.api.schemas.clients.ClientListResponse
import org.athletica.crm.api.schemas.clients.CreateClientRequest
import org.athletica.crm.api.schemas.clients.DeleteClientDocRequest
import org.athletica.crm.api.schemas.clients.EditClientRequest
import org.athletica.crm.api.schemas.clients.RemoveClientFromGroupRequest
import org.athletica.crm.api.schemas.clients.field
import org.athletica.crm.api.schemas.common.PerformedBy
import org.athletica.crm.core.Gender
import org.athletica.crm.core.customfields.CustomFieldDefinition
import org.athletica.crm.core.customfields.CustomFieldValues
import org.athletica.crm.core.customfields.displayValue
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.money.formatted
import org.athletica.crm.domain.clientbalance.ClientBalance
import org.athletica.crm.domain.clientbalance.ClientBalanceEntry
import org.athletica.crm.domain.clientbalance.ClientBalances
import org.athletica.crm.domain.clients.Client
import org.athletica.crm.domain.clients.Clients
import org.athletica.crm.domain.clients.clientDoc
import org.athletica.crm.domain.customfields.CustomFieldDefinitions
import org.athletica.crm.domain.employees.Employees
import org.athletica.crm.domain.enrollments.Enrollments
import org.athletica.crm.storage.Database

/**
 * Регистрирует маршруты для работы с клиентами:
 * Требует контекстных параметров [Database] и [AuditLog].
 */
context(db: Database)
fun RouteWithContext.clientsRoutes(
    clients: Clients,
    balances: ClientBalances,
    employees: Employees,
    enrollments: Enrollments,
    definitions: CustomFieldDefinitions,
) {
    get<Unit, ClientListResponse>("/clients/list") {
        db.transaction {
            val clientList = clients.list()
            val balancesByClient = balances.currentOf(clientList).associateBy { it.clientId }
            val items = clientList.map { it.toListItem(balancesByClient.getValue(it.id)) }
            ClientListResponse(items, items.size.toUInt())
        }
    }

    post<ClientExportRequest, ByteArray>("/clients/export") { request, call ->
        val format = call.request.queryParameters["format"] ?: "csv"

        val (items, customDefs) =
            db.transaction {
                val clientList = clients.list()
                val customDefs = definitions.all(CLIENT_ENTITY_TYPE)
                val balancesByClient = balances.currentOf(clientList).associateBy { it.clientId }

                val items = clientList.map { it.toListItem(balancesByClient.getValue(it.id)) }
                Pair(items, customDefs)
            }

        val csvContent = generateCsvContent(items, request.fields, customDefs)

        call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=\"clients.${format}\"")
        call.response.headers.append(
            HttpHeaders.ContentType,
            if (format == "xlsx") "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" else "text/csv; charset=UTF-8",
        )

        csvContent
    }

    get<ClientDetailRequest, ClientDetailResponse>("/clients/detail") { request ->
        db.transaction {
            val client = clients.byId(request.id)
            client.detailResponse(balances.currentOf(client))
        }
    }

    post<CreateClientRequest, ClientDetailResponse>("/clients/create") { request ->
        db.transaction {
            val defs = definitions.all(CLIENT_ENTITY_TYPE)
            val customFields = CustomFieldValues(defs).with(request.customFields).bind()
            val client =
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
            val balance = balances.currentOf(client)
            client.detailResponse(balance)
        }
    }

    post<EditClientRequest, ClientDetailResponse>("/clients/edit") { request ->
        db.transaction {
            val defs = definitions.all(CLIENT_ENTITY_TYPE)
            val customFields = CustomFieldValues(defs).with(request.customFields).bind()
            val client =
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
            val balance = balances.currentOf(client)
            client.detailResponse(balance)
        }
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
            val client = clients.byId(request.clientId)
            val balance =
                balances
                    .currentOf(client)
                    .adjust(request.amount, request.note)
            client.detailResponse(balance)
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
            parZip(
                { balances.currentOf(clients.byId(request.id)) },
                { employees.list().associate { it.id to PerformedBy(it.id.value, it.name) } },
            ) { balance, performedById ->
                ClientBalanceHistoryResponse(
                    entries = balance.history().map { it.toJournalEntry(performedById) },
                )
            }
        }
    }
}

fun Client.detailResponse(balance: ClientBalance) =
    ClientDetailResponse(
        id = id,
        name = name,
        avatarId = avatarId,
        birthday = birthday,
        gender = gender,
        groups = groups.map { ClientGroup(it.id, it.name) },
        balance = balance.totalAmount,
        docs = docs.map { ClientDoc(it.id, it.uploadId, it.name, it.createdAt) },
        leadSourceId = leadSourceId,
        customFields = customFields,
    )

fun Client.toListItem(balance: ClientBalance) =
    ClientListItem(
        id = id,
        name = name,
        avatarId = avatarId,
        birthday = birthday,
        gender = gender,
        groups = groups.map { ClientGroup(it.id, it.name) },
        balance = balance.totalAmount,
        customFields = customFields,
    )

private const val CLIENT_ENTITY_TYPE = "CLIENT"

private fun ClientBalanceEntry.toJournalEntry(performedById: Map<EmployeeId, PerformedBy>) =
    BalanceJournalEntry(
        id = id,
        amount = amount,
        balanceAfter = balanceAfter,
        operationType = operationType,
        note = note,
        performedBy = performedById[performedBy],
        createdAt = createdAt,
    )

/**
 * Генерирует CSV контент для экспорта списка клиентов.
 * Колонка «Имя» всегда первая. Остальные колонки соответствуют [fields] в порядке передачи.
 * Неизвестные ключи (не из [ClientField] и не из [customDefs]) молча пропускаются.
 * Возвращает ByteArray с CSV данными в кодировке UTF-8.
 */
private fun generateCsvContent(
    clients: List<ClientListItem>,
    fields: List<String>,
    customDefs: List<CustomFieldDefinition>,
): ByteArray {
    val customByKey = customDefs.associateBy { it.fieldKey.value }
    val resolved =
        fields.mapNotNull { key ->
            ClientField.byKey(key)?.let { ResolvedField.Standard(it) }
                ?: customByKey[key]?.let { ResolvedField.Custom(it) }
        }

    val sb = StringBuilder()
    val header = listOf("Имя") + resolved.map { it.header() }
    sb.appendLine(header.joinToString(","))

    clients.forEach { client ->
        val row = listOf(client.name) + resolved.map { it.value(client) }
        sb.appendLine(row.joinToString(","))
    }

    return sb.toString().toByteArray(Charsets.UTF_8)
}

/** Колонка экспорта, разрешённая в стандартное или кастомное поле. */
private sealed class ResolvedField {
    abstract fun header(): String

    abstract fun value(client: ClientListItem): String

    data class Standard(val field: ClientField) : ResolvedField() {
        override fun header(): String =
            when (field) {
                ClientField.BIRTHDAY -> "Дата рождения"
                ClientField.GENDER -> "Пол"
                ClientField.GROUPS -> "Группы"
                ClientField.BALANCE -> "Баланс"
            }

        override fun value(client: ClientListItem): String =
            when (field) {
                ClientField.BIRTHDAY -> client.birthday?.toString() ?: ""
                ClientField.GENDER ->
                    when (client.gender) {
                        Gender.MALE -> "М"
                        Gender.FEMALE -> "Ж"
                    }

                ClientField.GROUPS -> client.groups.joinToString("; ") { it.name }
                ClientField.BALANCE -> client.balance.formatted
            }
    }

    data class Custom(val definition: CustomFieldDefinition) : ResolvedField() {
        override fun header(): String = definition.label

        override fun value(client: ClientListItem): String = client.field(definition.fieldKey.value)?.displayValue() ?: ""
    }
}
