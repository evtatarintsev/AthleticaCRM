import Foundation
import Vapor

/// Зависимости маршрутов клиентов.
struct ClientsDeps: Sendable {
    /// База данных.
    let db: Database
    /// Репозиторий клиентов (с аудитом).
    let clients: any Clients
    /// Контакты клиентов.
    let contacts: any ClientContacts
    /// Записи в группы (с аудитом).
    let enrollments: any Enrollments
    /// Проекция списка клиентов.
    let clientList: any ClientListView
    /// Проекция баланса клиента.
    let clientBalance: any ClientBalanceView
}

/// Максимальный размер страницы списка клиентов.
private let maxClientPageSize = 100

extension RouteWithContext {
    /// Регистрирует маршруты работы с клиентами, перенесённые с Kotlin-сервера.
    /// `clients/export` и `clients/unpaid-sessions` остаются на Kotlin (см. `nginx/prod.conf.template`).
    func clientsRoutes(_ deps: ClientsDeps) {
        post("clients", "list") { (request: ClientListRequest, ctx) throws(AppError) in
            try await deps.db.transaction { tr throws(AppError) in
                let today = try await orgToday(ctx: ctx, tr: tr)
                return try await deps.clientList.page(try request.query(today: today, ctx: ctx), ctx: ctx, tr: tr)
            }
        }

        get("clients", "detail") { (request: ClientDetailRequest, ctx) throws(AppError) in
            try await deps.db.transaction { tr throws(AppError) in
                let client = try await deps.clients.byId(request.id, ctx: ctx, tr: tr)
                return try await detailResponse(client.data, client.state, deps, ctx: ctx, tr: tr)
            }
        }

        post("clients", "create") { (request: CreateClientRequest, ctx) throws(AppError) in
            try await deps.db.transaction { tr throws(AppError) in
                let client = try await deps.clients.new(request.id, request.fields, ctx: ctx, tr: tr)
                try await deps.contacts.replace(client, request.contacts.toDomain(client.data.id), ctx: ctx, tr: tr)
                return try await detailResponse(client.data, .ACTIVE, deps, ctx: ctx, tr: tr)
            }
        }

        post("clients", "edit") { (request: EditClientRequest, ctx) throws(AppError) in
            try await deps.db.transaction { tr throws(AppError) in
                let client = try await deps.clients
                    .activeById(request.id, ctx: ctx, tr: tr)
                    .withNew(request.fields, ctx: ctx, tr: tr)
                try await client.save(tr: tr)
                try await deps.contacts.replace(client, request.contacts.toDomain(client.data.id), ctx: ctx, tr: tr)
                return try await detailResponse(client.data, .ACTIVE, deps, ctx: ctx, tr: tr)
            }
        }

        post("clients", "add-to-group") { (request: AddClientsToGroupRequest, ctx) throws(AppError) in
            try await deps.db.transaction { tr throws(AppError) in
                try await deps.enrollments.add(request.groupId, request.clientIds, ctx: ctx, tr: tr)
                return Empty()
            }
        }

        post("clients", "add-to-groups") { (request: AddClientToGroupsRequest, ctx) throws(AppError) in
            try await deps.db.transaction { tr throws(AppError) in
                try await deps.enrollments.addToGroups(request.clientId, request.groupIds, ctx: ctx, tr: tr)
                return Empty()
            }
        }

        post("clients", "remove-from-group") { (request: RemoveClientFromGroupRequest, ctx) throws(AppError) in
            try await deps.db.transaction { tr throws(AppError) in
                try await deps.enrollments.remove(request.groupId, request.clientIds, ctx: ctx, tr: tr)
                return Empty()
            }
        }

        post("clients", "docs", "attach") { (request: AttachClientDocRequest, ctx) throws(AppError) in
            try await deps.db.transaction { tr throws(AppError) in
                try await deps.clients
                    .activeById(request.clientId, ctx: ctx, tr: tr)
                    .attachDoc(.new(uploadId: request.uploadId, name: request.name, id: request.docId), ctx: ctx)
                    .save(tr: tr)
                return Empty()
            }
        }

        post("clients", "docs", "delete") { (request: DeleteClientDocRequest, ctx) throws(AppError) in
            try await deps.db.transaction { tr throws(AppError) in
                try await deps.clients
                    .activeById(request.clientId, ctx: ctx, tr: tr)
                    .deleteDoc(request.docId, ctx: ctx)
                    .save(tr: tr)
                return Empty()
            }
        }

        post("clients", "archive") { (request: ClientIdsRequest, ctx) throws(AppError) in
            try await deps.db.transaction { tr throws(AppError) in
                for id in request.clientIds {
                    try await deps.clients.archive(id, ctx: ctx, tr: tr)
                }
                return Empty()
            }
        }

        post("clients", "restore") { (request: ClientIdsRequest, ctx) throws(AppError) in
            try await deps.db.transaction { tr throws(AppError) in
                for id in request.clientIds {
                    try await deps.clients.restore(id, ctx: ctx, tr: tr)
                }
                return Empty()
            }
        }
    }
}

/// Полные данные клиента [client] в состоянии [state] с балансом и контактами.
private func detailResponse(
    _ client: ClientData, _ state: ClientState, _ deps: ClientsDeps, ctx: EmployeeRequestContext, tr: Transaction
) async throws(AppError) -> ClientDetailResponse {
    let balance = try await deps.clientBalance.byClient(client.id, ctx: ctx, tr: tr)
    let contacts = try await deps.contacts.byClient(client.id, ctx: ctx, tr: tr)
    return ClientDetailResponse(
        id: client.id,
        name: client.name,
        createdAt: client.createdAt,
        avatarId: client.avatarId,
        birthday: client.birthday,
        gender: client.gender,
        groups: client.groups,
        balance: balance,
        docs: client.docs.map { ClientDocSchema(id: $0.id, uploadId: $0.uploadId, name: $0.name, createdAt: $0.createdAt) },
        leadSourceId: client.leadSourceId,
        customFields: client.customFields,
        contacts: contacts.map {
            ClientContactSchema(id: $0.id, type: $0.type, value: $0.value, label: $0.label, isPrimary: $0.isPrimary)
        },
        state: state
    )
}

extension ClientInState {
    /// Состояние клиента в терминах API-схемы (выводится из типа доменной сущности).
    fileprivate var state: ClientState {
        switch self {
        case .active: .ACTIVE
        case .archived: .ARCHIVED
        }
    }
}

/// «Сегодня» организации в её часовом поясе (порт `DbOrgCalendar.today`).
private func orgToday(ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) -> LocalDate {
    let timezone = try await tr.first("SELECT timezone FROM organizations WHERE id = \(ctx.orgId.value)") { row in
        try row.decode(String.self)
    }
    guard let timezone, let zone = TimeZone(identifier: timezone) else {
        throw .infrastructure("Не найден часовой пояс организации \(ctx.orgId)")
    }
    return LocalDate(Date(), in: zone)
}

extension Array where Element == ClientContactInput {
    /// Контакты запроса как доменные сущности клиента [clientId] с новыми идентификаторами.
    func toDomain(_ clientId: ClientId) -> [ClientContact] {
        map { ClientContact(id: .new(), clientId: clientId, type: $0.type, value: $0.value, label: $0.label, isPrimary: $0.isPrimary) }
    }
}

extension ClientListRequest {
    /// Параметры выборки из запроса API: нормализует пагинацию и сортировку (непустой `sort`
    /// заменяет `sortField`/`sortDirection`). Повтор поля сортировки или `NEXT_BIRTHDAY`
    /// без фильтра по дню рождения — `INVALID_CLIENT_SORT`.
    func query(today: LocalDate, ctx: EmployeeRequestContext) throws(AppError) -> ClientListQuery {
        let criteria = (sort?.isEmpty == false ? sort : nil) ?? [ClientSort(field: sortField, direction: sortDirection)]
        if Set(criteria.map(\.field)).count != criteria.count || (birthday == nil && criteria.contains { $0.field == .NEXT_BIRTHDAY }) {
            throw .domain("INVALID_CLIENT_SORT", Messages.invalidClientSort.localize(ctx))
        }
        let search = name.flatMap { $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : $0 }
        return ClientListQuery(
            today: today,
            archived: archived,
            search: search,
            gender: gender,
            hasDebt: hasDebt,
            noGroup: noGroup,
            groupId: groupId,
            birthday: birthday,
            sort: criteria.map { criterion in
                let column: ClientSortColumn = switch criterion.field {
                case .NAME: .name
                case .BALANCE: .balance
                case .BIRTHDAY: .birthday
                case .NEXT_BIRTHDAY: .nextBirthday
                }
                return ClientSortCriterion(column: column, ascending: criterion.direction == .Asc)
            },
            limit: Swift.min(Swift.max(limit, 1), maxClientPageSize),
            offset: Swift.max(offset, 0)
        )
    }
}
