package org.athletica.crm.routes

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.ktor.http.HttpHeaders
import org.athletica.crm.api.schemas.clients.AddClientToGroupsRequest
import org.athletica.crm.api.schemas.clients.AddClientsToGroupRequest
import org.athletica.crm.api.schemas.clients.ArchiveClientRequest
import org.athletica.crm.api.schemas.clients.AttachClientDocRequest
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
import org.athletica.crm.api.schemas.clients.ClientSort
import org.athletica.crm.api.schemas.clients.ClientSortField
import org.athletica.crm.api.schemas.clients.ClientState
import org.athletica.crm.api.schemas.clients.ClientUnpaidSessionsRequest
import org.athletica.crm.api.schemas.clients.CreateClientRequest
import org.athletica.crm.api.schemas.clients.DeleteClientDocRequest
import org.athletica.crm.api.schemas.clients.EditClientRequest
import org.athletica.crm.api.schemas.clients.RemoveClientFromGroupRequest
import org.athletica.crm.api.schemas.clients.RestoreClientRequest
import org.athletica.crm.api.schemas.clients.field
import org.athletica.crm.api.schemas.memberships.UnpaidSessionsResponse
import org.athletica.crm.api.schemas.settings.SortDirectionSchema
import org.athletica.crm.core.Gender
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.customfields.CustomFieldDefinition
import org.athletica.crm.core.customfields.displayValue
import org.athletica.crm.core.entityids.ClientContactId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.money.formatted
import org.athletica.crm.domain.clientcontacts.ClientContact
import org.athletica.crm.domain.clientcontacts.ClientContacts
import org.athletica.crm.domain.clients.ActiveClient
import org.athletica.crm.domain.clients.ArchivedClient
import org.athletica.crm.domain.clients.Client
import org.athletica.crm.domain.clients.Clients
import org.athletica.crm.domain.clients.activeById
import org.athletica.crm.domain.clients.archive
import org.athletica.crm.domain.clients.clientDoc
import org.athletica.crm.domain.clients.restore
import org.athletica.crm.domain.customfields.CLIENT_CUSTOM_FIELD_ENTITY_TYPE
import org.athletica.crm.domain.customfields.CustomFieldDefinitions
import org.athletica.crm.domain.employees.Employees
import org.athletica.crm.domain.enrollments.Enrollments
import org.athletica.crm.domain.enrollments.addToGroups
import org.athletica.crm.i18n.Messages
import org.athletica.crm.read.ReadViews
import org.athletica.crm.read.clients.ClientListQuery
import org.athletica.crm.read.clients.ClientSortColumn
import org.athletica.crm.read.clients.ClientSortCriterion
import org.athletica.crm.storage.Database

/**
 * Регистрирует маршруты для работы с клиентами:
 * Требует контекстных параметров [Database] и [AuditLog].
 */
context(db: Database)
fun RouteWithContext.clientsRoutes(
    clients: Clients,
    views: ReadViews,
    employees: Employees,
    enrollments: Enrollments,
    definitions: CustomFieldDefinitions,
    contacts: ClientContacts,
) {
    post<ClientUnpaidSessionsRequest, UnpaidSessionsResponse>("/clients/unpaid-sessions") { request ->
        db.transaction { views.unpaidSessions.byClient(request.clientId) }
    }

    post<ClientListRequest, ClientListResponse>("/clients/list") { request ->
        db.transaction {
            views.clientList.page(request.toQuery())
        }
    }

    post<ClientExportRequest, ByteArray>("/clients/export") { request, call ->
        val format = call.request.queryParameters["format"] ?: "csv"

        val (items, customDefs) =
            db.transaction {
                val items = views.clientList.page(ClientListQuery(limit = null)).clients
                Pair(items, definitions.all(CLIENT_CUSTOM_FIELD_ENTITY_TYPE))
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
            client.detailResponse(views.clientBalance.byClient(client.id), contacts.byClient(client.id))
        }
    }

    post<CreateClientRequest, ClientDetailResponse>("/clients/create") { request ->
        db.transaction {
            val client =
                clients
                    .new(
                        request.id,
                        request.name,
                        request.avatarId,
                        request.birthday,
                        request.gender,
                        request.leadSourceId,
                        request.customFields,
                    )
            contacts.replace(client, request.contacts.toDomain(client.id))
            val balance = views.clientBalance.byClient(client.id)
            client.detailResponse(balance, contacts.byClient(client.id))
        }
    }

    post<EditClientRequest, ClientDetailResponse>("/clients/edit") { request ->
        db.transaction {
            val client =
                clients
                    .activeById(request.id)
                    .withNew(
                        request.name,
                        request.avatarId,
                        request.birthday,
                        request.gender,
                        request.leadSourceId,
                        request.customFields,
                    )
                    .apply { save() }
            contacts.replace(client, request.contacts.toDomain(client.id))
            val balance = views.clientBalance.byClient(client.id)
            client.detailResponse(balance, contacts.byClient(client.id))
        }
    }

    post<AddClientsToGroupRequest, Unit>("/clients/add-to-group") { request ->
        db.transaction {
            enrollments.add(request.groupId, request.clientIds)
        }
    }

    post<AddClientToGroupsRequest, Unit>("/clients/add-to-groups") { request ->
        db.transaction {
            enrollments.addToGroups(request.clientId, request.groupIds)
        }
    }

    post<RemoveClientFromGroupRequest, Unit>("/clients/remove-from-group") { request ->
        db.transaction {
            enrollments.remove(request.groupId, request.clientIds)
        }
    }

    post<AttachClientDocRequest, Unit>("/clients/docs/attach") { request ->
        db.transaction {
            clients
                .activeById(request.clientId)
                .attachDoc(clientDoc(request.uploadId, request.name, request.docId))
                .save()
        }
    }

    post<DeleteClientDocRequest, Unit>("/clients/docs/delete") { request ->
        db.transaction {
            clients
                .activeById(request.clientId)
                .deleteDoc(request.docId)
                .save()
        }
    }

    post<ArchiveClientRequest, Unit>("/clients/archive") { request ->
        db.transaction {
            request.clientIds.forEach { id -> clients.archive(id) }
        }
    }

    post<RestoreClientRequest, Unit>("/clients/restore") { request ->
        db.transaction {
            request.clientIds.forEach { id -> clients.restore(id) }
        }
    }
}

fun Client.detailResponse(balance: Money, contacts: List<ClientContact>) =
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

/** Маппинг доменного контакта клиента в схему ответа. */
private fun ClientContact.toSchema(): ClientContactSchema =
    ClientContactSchema(
        id = id,
        type = type,
        value = value,
    )

/** Маппинг контактов из запроса в доменные сущности клиента [clientId] с новыми идентификаторами. */
private fun List<ClientContactInput>.toDomain(clientId: ClientId): List<ClientContact> = map { ClientContact(ClientContactId.new(), clientId, it.type, it.value) }

/** Максимальный размер страницы списка клиентов: защищает от чрезмерных запросов. */
private const val MAX_CLIENT_PAGE_SIZE = 100

/**
 * Преобразует запрос списка из API в параметры выборки: нормализует пагинацию и сортировку
 * (непустой `sort` заменяет одиночные `sortField`/`sortDirection`). Поднимает `INVALID_CLIENT_SORT`,
 * если поле сортировки повторяется или `NEXT_BIRTHDAY` задан без фильтра по дню рождения.
 */
context(ctx: RequestContext, raise: Raise<AppError>)
internal fun ClientListRequest.toQuery(): ClientListQuery {
    val criteria = sort?.takeIf { it.isNotEmpty() } ?: listOf(ClientSort(sortField, sortDirection))
    if (criteria.map { it.field }.distinct().size != criteria.size ||
        (birthday == null && criteria.any { it.field == ClientSortField.NEXT_BIRTHDAY })
    ) {
        raise(CommonDomainError("INVALID_CLIENT_SORT", Messages.InvalidClientSort.localize()))
    }
    return ClientListQuery(
        archived = archived,
        search = name?.takeIf { it.isNotBlank() },
        gender = gender,
        hasDebt = hasDebt,
        noGroup = noGroup,
        groupId = groupId,
        birthday = birthday,
        sort =
            criteria.map { criterion ->
                ClientSortCriterion(
                    column =
                        when (criterion.field) {
                            ClientSortField.NAME -> ClientSortColumn.NAME
                            ClientSortField.BALANCE -> ClientSortColumn.BALANCE
                            ClientSortField.BIRTHDAY -> ClientSortColumn.BIRTHDAY
                            ClientSortField.NEXT_BIRTHDAY -> ClientSortColumn.NEXT_BIRTHDAY
                        },
                    ascending = criterion.direction == SortDirectionSchema.Asc,
                )
            },
        limit = limit.coerceIn(1, MAX_CLIENT_PAGE_SIZE),
        offset = offset.coerceAtLeast(0),
    )
}

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
