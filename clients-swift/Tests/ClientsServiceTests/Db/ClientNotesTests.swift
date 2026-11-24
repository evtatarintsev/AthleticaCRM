import Foundation
import Testing
@testable import ClientsService

/// DB-тесты заметок о клиентах — порт `DbClientNotesTest.kt`.
@Suite struct ClientNotesTests {
    private let notes = DbClientNotes()

    /// Текст заметки из заведомо валидной строки.
    private func text(_ raw: String) -> ClientNoteText { try! ClientNoteText.from(raw) }

    @Test func `add создаёт заметку от имени сотрудника контекста`() async throws {
        let org = try await TestOrg.make()
        let client = try await org.client("Анна")
        let before = Date()

        let note = try await org.tx { ctx, tr throws(AppError) in try await notes.add(client, text("  первая  "), ctx: ctx, tr: tr) }

        #expect(note.authorId == org.ctx.employeeId)
        #expect(note.text.value == "первая")
        #expect(note.updatedAt == nil)
        #expect(note.createdAt >= before.addingTimeInterval(-1))
        let stored = try await org.tx { ctx, tr throws(AppError) in try await notes.list(client, ctx: ctx, tr: tr) }
        #expect(stored.map(\.id) == [note.id])
    }

    @Test func `list отдаёт новые сверху и без удалённых`() async throws {
        let org = try await TestOrg.make()
        let client = try await org.client("Анна")
        let first = try await org.tx { ctx, tr throws(AppError) in try await notes.add(client, text("1"), ctx: ctx, tr: tr) }
        let second = try await org.tx { ctx, tr throws(AppError) in try await notes.add(client, text("2"), ctx: ctx, tr: tr) }
        let third = try await org.tx { ctx, tr throws(AppError) in try await notes.add(client, text("3"), ctx: ctx, tr: tr) }
        try await org.tx { ctx, tr throws(AppError) in try await second.delete(ctx: ctx, tr: tr) }

        let list = try await org.tx { ctx, tr throws(AppError) in try await notes.list(client, ctx: ctx, tr: tr) }

        #expect(list.map(\.id) == [third.id, first.id])
    }

    @Test func `list изолирован по организации`() async throws {
        let org = try await TestOrg.make()
        let other = try await TestOrg.make()
        let client = try await org.client("Анна")
        try await org.tx { ctx, tr throws(AppError) in try await notes.add(client, text("моя"), ctx: ctx, tr: tr) }

        let foreign = try await other.tx { ctx, tr throws(AppError) in try await notes.list(client, ctx: ctx, tr: tr) }

        #expect(foreign.isEmpty)
    }

    @Test func `byId чужой организации — CLIENT_NOTE_NOT_FOUND`() async throws {
        let org = try await TestOrg.make()
        let other = try await TestOrg.make()
        let client = try await org.client("Анна")
        let note = try await org.tx { ctx, tr throws(AppError) in try await notes.add(client, text("моя"), ctx: ctx, tr: tr) }

        await expectDomainError("CLIENT_NOTE_NOT_FOUND") { () throws(AppError) in
            _ = try await other.tx { ctx, tr throws(AppError) in try await notes.byId(note.id, ctx: ctx, tr: tr) }
        }
    }

    @Test func `withText автора проставляет updatedAt и сохраняет текст`() async throws {
        let org = try await TestOrg.make()
        let client = try await org.client("Анна")
        let note = try await org.tx { ctx, tr throws(AppError) in try await notes.add(client, text("было"), ctx: ctx, tr: tr) }

        try await org.tx { ctx, tr throws(AppError) in
            try await notes.byId(note.id, ctx: ctx, tr: tr).withText(text("стало"), ctx: ctx).save(tr: tr)
        }

        let stored = try await org.tx { ctx, tr throws(AppError) in try await notes.byId(note.id, ctx: ctx, tr: tr) }
        #expect(stored.text.value == "стало")
        #expect(stored.updatedAt != nil)
    }

    @Test func `withText и delete не автора — PERMISSION_DENIED, заметка не меняется`() async throws {
        let org = try await TestOrg.make()
        let stranger = try await org.employee("Чужой")
        let client = try await org.client("Анна")
        let note = try await org.tx { ctx, tr throws(AppError) in try await notes.add(client, text("моя"), ctx: ctx, tr: tr) }

        await expectDomainError("PERMISSION_DENIED") { () throws(AppError) in
            _ = try await org.db.transaction { tr throws(AppError) in
                try await notes.byId(note.id, ctx: stranger, tr: tr).withText(text("чужая"), ctx: stranger)
            }
        }
        await expectDomainError("PERMISSION_DENIED") { () throws(AppError) in
            try await org.db.transaction { tr throws(AppError) in try await notes.byId(note.id, ctx: stranger, tr: tr).delete(ctx: stranger, tr: tr) }
        }
        let list = try await org.tx { ctx, tr throws(AppError) in try await notes.list(client, ctx: ctx, tr: tr) }
        #expect(list.map(\.text.value) == ["моя"])
    }

    @Test func `delete автором убирает заметку из list и проставляет deleted_at`() async throws {
        let org = try await TestOrg.make()
        let client = try await org.client("Анна")
        let note = try await org.tx { ctx, tr throws(AppError) in try await notes.add(client, text("моя"), ctx: ctx, tr: tr) }

        try await org.tx { ctx, tr throws(AppError) in try await note.delete(ctx: ctx, tr: tr) }

        let list = try await org.tx { ctx, tr throws(AppError) in try await notes.list(client, ctx: ctx, tr: tr) }
        #expect(list.isEmpty)
        #expect(try await org.int("SELECT COUNT(*) FROM client_notes WHERE id = \(note.id.value) AND deleted_at IS NOT NULL") == 1)
    }

    @Test func `текст заметки: пустой и длиннее 2000 символов отклоняются`() {
        #expect(throws: AppError.domain("EMPTY_CLIENT_NOTE_TEXT", "Текст заметки не может быть пустым")) { try ClientNoteText.from("   ") }
        #expect(throws: AppError.self) { try ClientNoteText.from(String(repeating: "я", count: 2001)) }
        #expect(throws: Never.self) { try ClientNoteText.from(String(repeating: "я", count: 2000)) }
    }
}
