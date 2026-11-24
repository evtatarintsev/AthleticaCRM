import Foundation

/// Запрос списка не удалённых заметок клиента.
struct ClientNotesListRequest: Decodable, Sendable {
    /// Клиент.
    let clientId: ClientId
}

/// Запрос на добавление заметки клиенту.
struct AddClientNoteRequest: Decodable, Sendable {
    /// Клиент.
    let clientId: ClientId
    /// Текст заметки.
    let text: ClientNoteText
}

/// Запрос на изменение текста заметки.
struct EditClientNoteRequest: Decodable, Sendable {
    /// Заметка.
    let noteId: ClientNoteId
    /// Новый текст.
    let text: ClientNoteText
}

/// Запрос на удаление заметки (soft-delete, только автором).
struct DeleteClientNoteRequest: Decodable, Sendable {
    /// Заметка.
    let noteId: ClientNoteId
}

/// Сотрудник, выполнивший действие: идентификатор и имя на момент чтения.
struct PerformedBy: Encodable, Equatable, Sendable {
    /// Идентификатор сотрудника.
    let id: EmployeeId
    /// Имя сотрудника.
    let name: String
}

/// Заметка клиента для отображения.
struct ClientNoteSchema: Encodable, Sendable {
    /// Идентификатор заметки.
    let id: ClientNoteId
    /// Текст.
    let text: ClientNoteText
    /// Автор.
    let author: PerformedBy
    /// Момент создания.
    let createdAt: Date
    /// Момент последнего редактирования; `null` — не редактировалась.
    @Nullable var updatedAt: Date?
}

/// Список заметок клиента.
struct ClientNotesListResponse: Encodable, Sendable {
    /// Заметки, новые сверху.
    let notes: [ClientNoteSchema]
}

/// Запрос неоплаченных посещений клиента.
struct ClientUnpaidSessionsRequest: Decodable, Sendable {
    /// Клиент.
    let clientId: ClientId
}

/// Неоплаченное занятие: проведено, отметка «пришёл», действующего списания нет.
struct UnpaidSessionSchema: Encodable, Equatable, Sendable {
    /// Занятие.
    let sessionId: SessionId
    /// Дата занятия.
    let date: LocalDate
    /// Время начала.
    let startTime: LocalTime
    /// Группа занятия.
    let groupId: GroupId
    /// Название группы.
    let groupName: String
}

/// Неоплаченные занятия, от старых к новым.
struct UnpaidSessionsResponse: Encodable, Sendable {
    /// Занятия.
    let sessions: [UnpaidSessionSchema]
}

/// Запрос экспорта клиентов: упорядоченные ключи колонок — стандартные ([ClientField]) или
/// кастомных полей. Колонка «Имя» всегда первая и не указывается.
struct ClientExportRequest: Decodable, Sendable {
    /// Ключи колонок.
    let fields: [String]
}

/// Стандартное поле клиента, доступное для экспорта.
enum ClientField: String, Sendable {
    case birthday, gender, groups, balance
}
