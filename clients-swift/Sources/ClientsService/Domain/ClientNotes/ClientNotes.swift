import Foundation
import PostgresNIO

/// Заметка о клиенте — неизменяемый агрегат: трансформации возвращают копию, в БД пишут
/// только [save] и [delete]. Править и удалять заметку может только её автор.
protocol ClientNote: Sendable {
    /// Идентификатор заметки.
    var id: ClientNoteId { get }
    /// Организация-владелец.
    var orgId: OrgId { get }
    /// Клиент, к которому относится заметка.
    var clientId: ClientId { get }
    /// Сотрудник, создавший заметку.
    var authorId: EmployeeId { get }
    /// Текст.
    var text: ClientNoteText { get }
    /// Момент создания.
    var createdAt: Date { get }
    /// Момент последнего редактирования; `nil` — не редактировалась.
    var updatedAt: Date? { get }

    /// Сохраняет текст и момент редактирования; используется после [withText].
    func save(tr: Transaction) async throws(AppError)

    /// Помечает заметку удалённой (soft-delete); не автор — `PERMISSION_DENIED`.
    func delete(ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError)

    /// Копия с текстом [newText] и заполненным `updatedAt` (без записи в БД); не автор — `PERMISSION_DENIED`.
    func withText(_ newText: ClientNoteText, ctx: EmployeeRequestContext) throws(AppError) -> any ClientNote
}

/// Репозиторий заметок о клиентах.
protocol ClientNotes: Sendable {
    /// Не удалённая заметка [id] организации; нет такой — `CLIENT_NOTE_NOT_FOUND`.
    func byId(_ id: ClientNoteId, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) -> any ClientNote

    /// Не удалённые заметки клиента [clientId], новые сверху.
    func list(_ clientId: ClientId, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) -> [any ClientNote]

    /// Создаёт заметку [text] у клиента [clientId] от имени текущего сотрудника.
    @discardableResult
    func add(_ clientId: ClientId, _ text: ClientNoteText, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) -> any ClientNote
}

/// Заметка, прочитанная из таблицы `client_notes`.
private struct DbClientNote: ClientNote {
    let id: ClientNoteId
    let orgId: OrgId
    let clientId: ClientId
    let authorId: EmployeeId
    let text: ClientNoteText
    let createdAt: Date
    let updatedAt: Date?

    func save(tr: Transaction) async throws(AppError) {
        try await tr.execute("""
            UPDATE client_notes SET text = \(text.value), updated_at = \(updatedAt)
            WHERE id = \(id.value) AND org_id = \(orgId.value) AND deleted_at IS NULL
            """)
    }

    func delete(ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) {
        try requireAuthor(ctx)
        try await tr.execute("""
            UPDATE client_notes SET deleted_at = now()
            WHERE id = \(id.value) AND org_id = \(ctx.orgId.value) AND deleted_at IS NULL
            """)
    }

    func withText(_ newText: ClientNoteText, ctx: EmployeeRequestContext) throws(AppError) -> any ClientNote {
        try requireAuthor(ctx)
        return DbClientNote(id: id, orgId: orgId, clientId: clientId, authorId: authorId, text: newText, createdAt: createdAt, updatedAt: Date())
    }

    /// Проверяет, что заметку меняет её автор; иначе — `PERMISSION_DENIED`.
    private func requireAuthor(_ ctx: EmployeeRequestContext) throws(AppError) {
        if authorId != ctx.employeeId {
            throw .domain("PERMISSION_DENIED", "Заметку может изменять только её автор")
        }
    }
}

/// Заметки о клиентах поверх PostgreSQL.
struct DbClientNotes: ClientNotes {
    func byId(_ id: ClientNoteId, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) -> any ClientNote {
        let note = try await tr.first("""
            SELECT id, org_id, client_id, author_id, text, created_at, updated_at
            FROM client_notes
            WHERE id = \(id.value) AND org_id = \(ctx.orgId.value) AND deleted_at IS NULL
            """, Self.decode)
        guard let note else {
            throw .domain("CLIENT_NOTE_NOT_FOUND", "Заметка не найдена")
        }
        return try note.parsed()
    }

    func list(_ clientId: ClientId, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) -> [any ClientNote] {
        let rows = try await tr.list("""
            SELECT id, org_id, client_id, author_id, text, created_at, updated_at
            FROM client_notes
            WHERE client_id = \(clientId.value) AND org_id = \(ctx.orgId.value) AND deleted_at IS NULL
            ORDER BY created_at DESC, id DESC
            """, Self.decode)
        var notes: [any ClientNote] = []
        for row in rows {
            notes.append(try row.parsed())
        }
        return notes
    }

    @discardableResult
    func add(_ clientId: ClientId, _ text: ClientNoteText, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) -> any ClientNote {
        let note = DbClientNote(
            id: .new(), orgId: ctx.orgId, clientId: clientId, authorId: ctx.employeeId, text: text, createdAt: Date(), updatedAt: nil
        )
        try await tr.execute("""
            INSERT INTO client_notes (id, org_id, client_id, author_id, text, created_at)
            VALUES (\(note.id.value), \(note.orgId.value), \(clientId.value), \(note.authorId.value), \(text.value), \(note.createdAt))
            """)
        return note
    }

    /// Сырые колонки строки заметки.
    private struct Row {
        let id: UUID, orgId: UUID, clientId: UUID, authorId: UUID, text: String, createdAt: Date, updatedAt: Date?

        /// Заметка из строки; невалидный текст в БД — `CLIENT_NOTE_TEXT_CORRUPTED`.
        func parsed() throws(AppError) -> DbClientNote {
            let parsedText: ClientNoteText
            do {
                parsedText = try ClientNoteText.from(text)
            } catch {
                throw .domain("CLIENT_NOTE_TEXT_CORRUPTED", "Невалидный текст заметки в БД")
            }
            return DbClientNote(
                id: ClientNoteId(id), orgId: OrgId(orgId), clientId: ClientId(clientId), authorId: EmployeeId(authorId),
                text: parsedText, createdAt: createdAt, updatedAt: updatedAt
            )
        }
    }

    /// Колонки строки `client_notes` в порядке выборки.
    private static func decode(_ row: PostgresRow) throws -> Row {
        let (id, orgId, clientId, authorId, text, createdAt, updatedAt) =
            try row.decode((UUID, UUID, UUID, UUID, String, Date, Date?).self)
        return Row(id: id, orgId: orgId, clientId: clientId, authorId: authorId, text: text, createdAt: createdAt, updatedAt: updatedAt)
    }
}
