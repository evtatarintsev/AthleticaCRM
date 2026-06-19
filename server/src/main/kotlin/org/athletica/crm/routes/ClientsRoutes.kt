package org.athletica.crm.routes

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import arrow.fx.coroutines.parZip
import io.ktor.http.HttpHeaders
import org.athletica.crm.api.schemas.clients.AddClientsToGroupRequest
import org.athletica.crm.api.schemas.clients.AdjustBalanceRequest
import org.athletica.crm.api.schemas.clients.ArchiveClientRequest
import org.athletica.crm.api.schemas.clients.AttachClientDocRequest
import org.athletica.crm.api.schemas.clients.BalanceJournalEntry
import org.athletica.crm.api.schemas.clients.ClientBalanceHistoryRequest
import org.athletica.crm.api.schemas.clients.ClientBalanceHistoryResponse
import org.athletica.crm.api.schemas.clients.ClientContactInput
import org.athletica.crm.api.schemas.clients.ClientContactSchema
import org.athletica.crm.api.schemas.clients.ClientDetailRequest
import org.athletica.crm.api.schemas.clients.ClientDetailResponse
import org.athletica.crm.api.schemas.clients.ClientDoc
import org.athletica.crm.api.schemas.clients.ClientExportRequest
import org.athletica.crm.api.schemas.clients.ClientField
import org.athletica.crm.api.schemas.clients.ClientGroup
import org.athletica.crm.api.schemas.clients.ClientListItem
import org.athletica.crm.api.schemas.clients.ClientListRequest
import org.athletica.crm.api.schemas.clients.ClientListResponse
import org.athletica.crm.api.schemas.clients.ClientSortField
import org.athletica.crm.api.schemas.clients.ClientState
import org.athletica.crm.api.schemas.clients.CreateClientRequest
import org.athletica.crm.api.schemas.clients.DeleteClientDocRequest
import org.athletica.crm.api.schemas.clients.EditClientRequest
import org.athletica.crm.api.schemas.clients.RemoveClientFromGroupRequest
import org.athletica.crm.api.schemas.clients.RestoreClientRequest
import org.athletica.crm.api.schemas.clients.field
import org.athletica.crm.api.schemas.common.PerformedBy
import org.athletica.crm.api.schemas.settings.SortDirectionSchema
import org.athletica.crm.core.Gender
import org.athletica.crm.core.customfields.CustomFieldDefinition
import org.athletica.crm.core.customfields.CustomFieldValues
import org.athletica.crm.core.customfields.displayValue
import org.athletica.crm.core.entityids.ClientContactId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.formatted
import org.athletica.crm.domain.clientbalance.ClientBalance
import org.athletica.crm.domain.clientbalance.ClientBalanceEntry
import org.athletica.crm.domain.clientbalance.ClientBalances
import org.athletica.crm.domain.clientcontacts.ClientContact
import org.athletica.crm.domain.clientcontacts.ClientContacts
import org.athletica.crm.domain.clients.ActiveClient
import org.athletica.crm.domain.clients.ArchivedClient
import org.athletica.crm.domain.clients.Client
import org.athletica.crm.domain.clients.ClientListQuery
import org.athletica.crm.domain.clients.ClientListRow
import org.athletica.crm.domain.clients.ClientListView
import org.athletica.crm.domain.clients.ClientSortColumn
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
    listView: ClientListView,
    balances: ClientBalances,
    employees: Employees,
    enrollments: Enrollments,
    definitions: CustomFieldDefinitions,
    contacts: ClientContacts,
) {
    post<ClientListRequest, ClientListResponse>("/clients/list") { request ->
        db.transaction {
            val page = listView.page(request.toQuery())
            ClientListResponse(
                clients = page.rows.map { it.toListItem() },
                total = page.total.toUInt(),
            )
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
            client.detailResponse(balances.currentOf(client), contacts.byClient(client.id))
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
            contacts.replace(client.id, request.contacts.toDomain(client.id))
            val balance = balances.currentOf(client)
            client.detailResponse(balance, contacts.byClient(client.id))
        }
    }

    post<EditClientRequest, ClientDetailResponse>("/clients/edit") { request ->
        db.transaction {
            val defs = definitions.all(CLIENT_ENTITY_TYPE)
            val customFields = CustomFieldValues(defs).with(request.customFields).bind()
            val client =
                clients
                    .byId(request.id)
                    .requireActive()
                    .withNew(
                        request.name,
                        request.avatarId,
                        request.birthday,
                        request.gender,
                        request.leadSourceId,
                        customFields.toList(),
                    )
                    .apply { save() }
            contacts.replace(client.id, request.contacts.toDomain(client.id))
            val balance = balances.currentOf(client)
            client.detailResponse(balance, contacts.byClient(client.id))
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
            val client = clients.byId(request.clientId).requireActive()
            val balance =
                balances
                    .currentOf(client)
                    .adjust(request.amount, request.note)
            client.detailResponse(balance, contacts.byClient(client.id))
        }
    }

    post<AttachClientDocRequest, Unit>("/clients/docs/attach") { request ->
        db.transaction {
            clients
                .byId(request.clientId)
                .requireActive()
                .attachDoc(clientDoc(request.uploadId, request.name))
                .save()
        }
    }

    post<DeleteClientDocRequest, Unit>("/clients/docs/delete") { request ->
        db.transaction {
            clients
                .byId(request.clientId)
                .requireActive()
                .deleteDoc(request.docId)
                .save()
        }
    }

    post<ArchiveClientRequest, Unit>("/clients/archive") { request ->
        db.transaction {
            request.clientIds.forEach { id ->
                when (val client = clients.byId(id)) {
                    is ActiveClient -> client.archive()
                    is ArchivedClient -> raise(CommonDomainError("CLIENT_ALREADY_ARCHIVED", "Клиент уже в архиве"))
                }
            }
        }
    }

    post<RestoreClientRequest, Unit>("/clients/restore") { request ->
        db.transaction {
            request.clientIds.forEach { id ->
                when (val client = clients.byId(id)) {
                    is ArchivedClient -> client.restore()
                    is ActiveClient -> raise(CommonDomainError("CLIENT_NOT_ARCHIVED", "Клиент не в архиве"))
                }
            }
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

fun Client.detailResponse(balance: ClientBalance, contacts: List<ClientContact>) =
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
        contacts = contacts.map { it.toSchema() },
        state = state,
    )

/** Состояние клиента в терминах API-схемы (выводится из типа доменной сущности). */
val Client.state: ClientState
    get() =
        when (this) {
            is ActiveClient -> ClientState.ACTIVE
            is ArchivedClient -> ClientState.ARCHIVED
        }

/**
 * Сужает клиента до [ActiveClient]; архивный клиент недоступен для изменения.
 */
context(raise: Raise<DomainError>)
fun Client.requireActive(): ActiveClient =
    when (this) {
        is ActiveClient -> this
        is ArchivedClient -> raise(CommonDomainError("CLIENT_ARCHIVED", "Клиент в архиве и недоступен для изменения"))
    }

/** Маппинг доменного контакта клиента в схему ответа. */
private fun ClientContact.toSchema(): ClientContactSchema =
    ClientContactSchema(
        id = id,
        type = type,
        value = value,
    )

/** Маппинг контактов из запроса в доменные сущности клиента [clientId] с новыми идентификаторами. */
private fun List<ClientContactInput>.toDomain(clientId: ClientId): List<ClientContact> = map { ClientContact(ClientContactId.new(), clientId, it.type, it.value) }

fun Client.toListItem(
    balance: ClientBalance,
    contacts: List<ClientContact> = emptyList(),
) = ClientListItem(
    id = id,
    name = name,
    avatarId = avatarId,
    birthday = birthday,
    gender = gender,
    groups = groups.map { ClientGroup(it.id, it.name) },
    balance = balance.totalAmount,
    customFields = customFields,
    contacts = contacts.map { it.toSchema() },
    state = state,
)

private const val CLIENT_ENTITY_TYPE = "CLIENT"

/** Максимальный размер страницы списка клиентов: защищает от чрезмерных запросов. */
private const val MAX_CLIENT_PAGE_SIZE = 100

/** Преобразует запрос списка из API в доменные параметры выборки с нормализацией пагинации. */
private fun ClientListRequest.toQuery(): ClientListQuery =
    ClientListQuery(
        archived = archived,
        search = name?.takeIf { it.isNotBlank() },
        gender = gender,
        hasDebt = hasDebt,
        noGroup = noGroup,
        groupId = groupId,
        birthday = birthday,
        sortColumn =
            when (sortField) {
                ClientSortField.NAME -> ClientSortColumn.NAME
                ClientSortField.BALANCE -> ClientSortColumn.BALANCE
                ClientSortField.BIRTHDAY -> ClientSortColumn.BIRTHDAY
            },
        ascending = sortDirection == SortDirectionSchema.Asc,
        limit = limit.coerceIn(1, MAX_CLIENT_PAGE_SIZE),
        offset = offset.coerceAtLeast(0),
    )

/** Собирает элемент ответа списка из строки проекции [ClientListRow]. */
private fun ClientListRow.toListItem(): ClientListItem =
    ClientListItem(
        id = id,
        name = name,
        avatarId = avatarId,
        birthday = birthday,
        gender = gender,
        groups = groups.map { ClientGroup(it.id, it.name) },
        balance = balance,
        customFields = customFields,
        contacts = contacts.map { it.toSchema() },
        state = if (archived) ClientState.ARCHIVED else ClientState.ACTIVE,
    )

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
