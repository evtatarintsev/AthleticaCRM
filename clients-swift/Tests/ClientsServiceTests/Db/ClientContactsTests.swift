import Foundation
import Testing
@testable import ClientsService

/// DB-тесты контактов клиента — порт `DbClientContactsTest.kt` (без `byClients`: в Swift-сервисе её нет).
@Suite struct DbClientContactsTests {
    private let contacts = DbClientContacts()

    /// Телефон [value] клиента [clientId] с комментарием [label].
    private func phone(_ clientId: ClientId, _ value: String, _ label: String? = nil, primary: Bool = false) -> ClientContact {
        ClientContact(id: .new(), clientId: clientId, type: .PHONE, value: value, label: label, isPrimary: primary)
    }

    /// Заменяет контакты клиента [clientId] на [list].
    private func replace(_ org: TestOrg, _ clientId: ClientId, _ list: [ClientContact]) async throws(AppError) {
        try await org.tx { ctx, tr throws(AppError) in
            try await contacts.replace(try await plainClients.activeById(clientId, ctx: ctx, tr: tr), list, ctx: ctx, tr: tr)
        }
    }

    /// Сохранённые контакты клиента [clientId]: комментарий и признак основного.
    private func stored(_ org: TestOrg, _ clientId: ClientId) async throws -> [String] {
        try await org.tx { ctx, tr throws(AppError) in try await contacts.byClient(clientId, ctx: ctx, tr: tr) }
            .map { "\($0.value):\($0.label ?? "-"):\($0.isPrimary)" }
    }

    @Test func `replace полностью заменяет набор и хранит его нормализованным`() async throws {
        let org = try await TestOrg.make()
        let client = try await org.client("Анна")
        try await replace(org, client, [phone(client, "1"), phone(client, "2")])

        try await replace(org, client, [
            phone(client, "3", "  мама "),
            ClientContact(id: .new(), clientId: client, type: .EMAIL, value: "a@b", label: "  ", isPrimary: false),
            phone(client, "4", primary: true),
        ])

        #expect(try await stored(org, client) == ["4:-:true", "3:мама:false", "a@b:-:true"])
    }

    @Test func `два основных телефона отклоняются и не меняют прежние контакты`() async throws {
        let org = try await TestOrg.make()
        let client = try await org.client("Анна")
        try await replace(org, client, [phone(client, "1", "мама")])

        await expectDomainError("CONTACT_PRIMARY_DUPLICATE") { () throws(AppError) in
            try await replace(org, client, [phone(client, "2", primary: true), phone(client, "3", primary: true)])
        }
        #expect(try await stored(org, client) == ["1:мама:true"])
    }

    @Test func `после удаления основного телефона основным становится следующий`() async throws {
        let org = try await TestOrg.make()
        let client = try await org.client("Анна")
        try await replace(org, client, [phone(client, "1", "мама", primary: true), phone(client, "2", "папа")])

        try await replace(org, client, [phone(client, "2", "папа")])

        #expect(try await stored(org, client) == ["2:папа:true"])
    }

    @Test func `база не даёт оставить тип без основного или с двумя основными`() async throws {
        let org = try await TestOrg.make()
        let client = try await org.client("Анна")
        try await replace(org, client, [phone(client, "1", "мама"), phone(client, "2", "папа")])

        for (primary, constraint) in [(false, "ck_client_contacts_one_primary"), (true, "uq_client_contacts_primary")] {
            do {
                try await org.sql("UPDATE client_contacts SET is_primary = \(primary) WHERE client_id = \(client.value)")
                Issue.record("Ожидалось нарушение ограничения")
            } catch let error as AppError {
                #expect(error == .constraintViolation(constraint))
            }
        }
        #expect(try await stored(org, client) == ["1:мама:true", "2:папа:false"])
    }

    @Test func `контакты чужой организации не видны`() async throws {
        let org = try await TestOrg.make()
        let other = try await TestOrg.make()
        let client = try await org.client("Анна")
        try await replace(org, client, [phone(client, "1")])

        let foreign = try await other.tx { ctx, tr throws(AppError) in try await contacts.byClient(client, ctx: ctx, tr: tr) }

        #expect(foreign.isEmpty)
    }
}
