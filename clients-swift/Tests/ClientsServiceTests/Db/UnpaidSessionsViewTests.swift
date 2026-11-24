import Foundation
import Testing
@testable import ClientsService

/// DB-тесты проекции неоплаченных занятий — порт `DbClientUnpaidSessionsViewTest.kt`.
@Suite struct UnpaidSessionsViewTests {
    private let view = DbUnpaidSessionsView()

    /// Неоплаченные занятия клиента [clientId] глазами владельца [org].
    private func unpaid(_ org: TestOrg, _ clientId: ClientId) async throws -> [SessionId] {
        try await org.tx { ctx, tr throws(AppError) in try await view.byClient(clientId, ctx: ctx, tr: tr) }.sessions.map(\.sessionId)
    }

    @Test func `только проведённые с отметкой «пришёл», по порядку, в своей организации`() async throws {
        let org = try await TestOrg.make()
        let foreignOrg = try await TestOrg.make()
        let client = try await org.client("Анна")
        let group = try await org.group("Группа")
        let other = try await org.group("Другая")
        let later = try await org.session(group, "2026-10-15")
        let earlier = try await org.session(other, "2026-10-13")
        let absent = try await org.session(group, "2026-10-14")
        let scheduled = try await org.session(group, "2026-10-16", status: "scheduled")
        let cancelled = try await org.session(group, "2026-10-17", status: "cancelled")
        for session in [later, earlier, scheduled, cancelled] {
            try await org.attend(session, client)
        }
        try await org.attend(absent, client, presence: "absent")

        #expect(try await unpaid(org, client) == [earlier, later])
        let foreignClient = try await foreignOrg.client("Чужой")
        for id in [ClientId.new(), foreignClient] {
            await expectDomainError("CLIENT_NOT_FOUND") { () throws(AppError) in
                _ = try await org.tx { ctx, tr throws(AppError) in try await view.byClient(id, ctx: ctx, tr: tr) }
            }
        }
    }

    @Test func `занятия другого филиала скрыты`() async throws {
        let org = try await TestOrg.make()
        let client = try await org.client("Анна")
        let local = try await org.session(try await org.group("Здесь"), "2026-10-13")
        let remote = try await org.session(try await org.group("Там", branchId: try await org.branch("Другой")), "2026-10-14")
        try await org.attend(local, client)
        try await org.attend(remote, client)

        #expect(try await unpaid(org, client) == [local])
    }

    @Test func `списание исключает занятие, сторно возвращает`() async throws {
        let org = try await TestOrg.make()
        let client = try await org.client("Анна")
        let session = try await org.session(try await org.group("Группа"), "2026-10-15", "09:30")
        try await org.attend(session, client)
        let response = try await org.tx { ctx, tr throws(AppError) in try await view.byClient(client, ctx: ctx, tr: tr) }
        #expect(response.sessions.map(\.startTime.description) == ["09:30"])

        let debit = try await org.debit(session, client)
        #expect(try await unpaid(org, client).isEmpty)

        try await org.sql("UPDATE membership_debits SET reversed_at = now() WHERE id = \(debit)")
        #expect(try await unpaid(org, client) == [session])
    }
}
