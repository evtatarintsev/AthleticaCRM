import Foundation

/// Группа, в которой состоит клиент (идентификатор и название для карточки).
struct ClientGroup: Codable, Equatable, Sendable {
    /// Идентификатор группы.
    let id: GroupId
    /// Название группы.
    let name: String
}

/// Документ, прикреплённый к клиенту.
struct ClientDoc: Equatable, Sendable {
    /// Идентификатор документа.
    let id: ClientDocId
    /// Загруженный файл документа.
    let uploadId: UploadId
    /// Отображаемое название документа.
    let name: String
    /// Момент прикрепления.
    let createdAt: Date

    /// Новый документ для файла [uploadId] с названием [name]; [id] генерирует веб-клиент.
    static func new(uploadId: UploadId, name: String, id: ClientDocId) -> ClientDoc {
        ClientDoc(id: id, uploadId: uploadId, name: name, createdAt: Date())
    }
}

/// Изменяемые поля клиента: задаются при создании и редактировании.
struct ClientFields: Sendable {
    /// Отображаемое имя.
    let name: NonBlankName
    /// Аватарка; `nil` — не задана.
    let avatarId: UploadId?
    /// День рождения; `nil` — не указан.
    let birthday: LocalDate?
    /// Пол.
    let gender: Gender
    /// Источник клиента; `nil` — не указан.
    let leadSourceId: LeadSourceId?
    /// Значения кастомных полей.
    let customFields: [CustomFieldValue]
}

/// Данные клиента организации, общие для любого состояния.
struct ClientData: Sendable {
    /// Идентификатор клиента.
    let id: ClientId
    /// Отображаемое имя.
    var name: String
    /// Момент создания записи.
    let createdAt: Date
    /// Аватарка; `nil` — не задана.
    var avatarId: UploadId?
    /// День рождения; `nil` — не указан.
    var birthday: LocalDate?
    /// Пол.
    var gender: Gender
    /// Группы, в которых клиент состоит сейчас.
    let groups: [ClientGroup]
    /// Прикреплённые документы.
    var docs: [ClientDoc]
    /// Источник клиента; `nil` — не указан.
    var leadSourceId: LeadSourceId?
    /// Значения кастомных полей.
    var customFields: [CustomFieldValue]
}

/// Клиент организации; состояние выражено типом: [ActiveClient] или [ArchivedClient].
protocol Client: Sendable {
    /// Данные клиента.
    var data: ClientData { get }
}

/// Активный клиент: доступны редактирование, документы и архивирование.
protocol ActiveClient: Client {
    /// Сохраняет изменённые поля и документы клиента в БД.
    func save(tr: Transaction) async throws(AppError)

    /// Переводит клиента в архив.
    func archive(ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError)

    /// Копия с прикреплённым документом [doc] (без записи в БД).
    func attachDoc(_ doc: ClientDoc, ctx: EmployeeRequestContext) throws(AppError) -> any ActiveClient

    /// Копия без документа [docId] (без записи в БД).
    func deleteDoc(_ docId: ClientDocId, ctx: EmployeeRequestContext) throws(AppError) -> any ActiveClient

    /// Копия с новыми значениями полей [fields] (без записи в БД); кастомные поля проверяются по определениям.
    func withNew(_ fields: ClientFields, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) -> any ActiveClient
}

/// Архивный клиент: доступно только восстановление.
protocol ArchivedClient: Client {
    /// Восстанавливает клиента из архива.
    func restore(ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError)
}

/// Клиент в одном из состояний; разбирается исчерпывающим `switch`, как `sealed interface` в Kotlin.
enum ClientInState: Sendable {
    /// Активный клиент.
    case active(any ActiveClient)
    /// Архивный клиент.
    case archived(any ArchivedClient)

    /// Данные клиента независимо от состояния.
    var data: ClientData {
        switch self {
        case let .active(client): client.data
        case let .archived(client): client.data
        }
    }
}

/// Репозиторий клиентов организации.
protocol Clients: Sendable {
    /// Клиент по [id]; тип результата отражает состояние. Нет такого — `CLIENT_NOT_FOUND`.
    func byId(_ id: ClientId, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) -> ClientInState

    /// Создаёт нового активного клиента [id] с полями [fields].
    func new(_ id: ClientId, _ fields: ClientFields, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) -> any ActiveClient
}

extension Clients {
    /// Активный клиент [id]; архивный — ошибка `CLIENT_ARCHIVED`.
    func activeById(_ id: ClientId, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) -> any ActiveClient {
        switch try await byId(id, ctx: ctx, tr: tr) {
        case let .active(client): client
        case .archived: throw ClientErrors.archived
        }
    }

    /// Архивирует клиента [id]; уже архивный — ошибка `CLIENT_ALREADY_ARCHIVED`.
    func archive(_ id: ClientId, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) {
        switch try await byId(id, ctx: ctx, tr: tr) {
        case let .active(client): try await client.archive(ctx: ctx, tr: tr)
        case .archived: throw ClientErrors.alreadyArchived
        }
    }

    /// Восстанавливает клиента [id] из архива; активный — ошибка `CLIENT_NOT_ARCHIVED`.
    func restore(_ id: ClientId, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) {
        switch try await byId(id, ctx: ctx, tr: tr) {
        case let .archived(client): try await client.restore(ctx: ctx, tr: tr)
        case .active: throw ClientErrors.notArchived
        }
    }
}

/// Ошибки состояния клиента.
enum ClientErrors {
    /// Клиент в архиве и недоступен для изменения.
    static let archived = AppError.domain("CLIENT_ARCHIVED", "Клиент в архиве и недоступен для изменения")
    /// Клиент уже в архиве.
    static let alreadyArchived = AppError.domain("CLIENT_ALREADY_ARCHIVED", "Клиент уже в архиве")
    /// Клиент не в архиве.
    static let notArchived = AppError.domain("CLIENT_NOT_ARCHIVED", "Клиент не в архиве")
}
