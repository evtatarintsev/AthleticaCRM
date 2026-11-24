import Foundation
import PostgresNIO
import Vapor

extension RouteWithContext {
    /// Регистрирует маршруты заметок о клиентах; каждый отвечает актуальным списком заметок клиента.
    func clientNotesRoutes(db: Database, notes: any ClientNotes) {
        get("clients", "notes", "list") { (request: ClientNotesListRequest, ctx) throws(AppError) in
            try await db.transaction { tr throws(AppError) in
                try await listResponse(notes, request.clientId, ctx: ctx, tr: tr)
            }
        }

        post("clients", "notes", "add") { (request: AddClientNoteRequest, ctx) throws(AppError) in
            try await db.transaction { tr throws(AppError) in
                try await notes.add(request.clientId, request.text, ctx: ctx, tr: tr)
                return try await listResponse(notes, request.clientId, ctx: ctx, tr: tr)
            }
        }

        post("clients", "notes", "edit") { (request: EditClientNoteRequest, ctx) throws(AppError) in
            try await db.transaction { tr throws(AppError) in
                let updated = try await notes.byId(request.noteId, ctx: ctx, tr: tr).withText(request.text, ctx: ctx)
                try await updated.save(tr: tr)
                return try await listResponse(notes, updated.clientId, ctx: ctx, tr: tr)
            }
        }

        post("clients", "notes", "delete") { (request: DeleteClientNoteRequest, ctx) throws(AppError) in
            try await db.transaction { tr throws(AppError) in
                let note = try await notes.byId(request.noteId, ctx: ctx, tr: tr)
                try await note.delete(ctx: ctx, tr: tr)
                return try await listResponse(notes, note.clientId, ctx: ctx, tr: tr)
            }
        }
    }
}

/// Актуальный список заметок клиента [clientId] с именами авторов: имя собирается здесь,
/// в домене у заметки только идентификатор автора.
private func listResponse(
    _ notes: any ClientNotes, _ clientId: ClientId, ctx: EmployeeRequestContext, tr: Transaction
) async throws(AppError) -> ClientNotesListResponse {
    let list = try await notes.list(clientId, ctx: ctx, tr: tr)
    let names = try await employeeNames(Set(list.map(\.authorId)), ctx: ctx, tr: tr)
    return ClientNotesListResponse(notes: list.map { note in
        ClientNoteSchema(
            id: note.id,
            text: note.text,
            author: PerformedBy(id: note.authorId, name: names[note.authorId] ?? ""),
            createdAt: note.createdAt,
            updatedAt: note.updatedAt
        )
    })
}

/// Имена сотрудников [ids] организации одной выборкой.
private func employeeNames(_ ids: Set<EmployeeId>, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) -> [EmployeeId: String] {
    if ids.isEmpty {
        return [:]
    }
    let rows = try await tr.list("""
        SELECT id, name FROM employees WHERE id = ANY(\(ids.map(\.value))) AND org_id = \(ctx.orgId.value)
        """) { row in try row.decode((UUID, String).self) }
    return Dictionary(rows.map { (EmployeeId($0.0), $0.1) }, uniquingKeysWith: { first, _ in first })
}
