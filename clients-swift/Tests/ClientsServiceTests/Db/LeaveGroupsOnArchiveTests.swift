import Foundation
import Testing
@testable import ClientsService

/// DB-тесты архивации с событием `ClientArchived` и его обработчика.
@Suite struct LeaveGroupsOnArchiveTests {
    /// Репозиторий клиентов с шиной событий.
    private let clients = DbClients(definitions: DbCustomFieldDefinitions(), events: DomainEventBus())

    @Test func `архивация публикует ClientArchived в той же транзакции`() async throws {
        let org = try await TestOrg.make()
        let client = try await org.client("Анна")

        try await org.tx { ctx, tr throws(AppError) in try await clients.archive(client, ctx: ctx, tr: tr) }

        let payload = try await org.tx { ctx, tr throws(AppError) in
            try await tr.first("""
                SELECT payload::text FROM domain_events WHERE org_id = \(ctx.orgId.value) AND event_type = 'ClientArchived'
                """) { try $0.decode(String.self) }
        }
        let event = try decodeJson(ClientArchived.self, try #require(payload))
        #expect(event.clientId == client && event.archivedBy == org.ctx.employeeId)
    }

    @Test func `ошибка архивации откатывает и событие`() async throws {
        let org = try await TestOrg.make()
        let client = try await org.client("Анна", archived: true)

        await expectDomainError("CLIENT_ALREADY_ARCHIVED") { () throws(AppError) in
            try await org.tx { ctx, tr throws(AppError) in try await clients.archive(client, ctx: ctx, tr: tr) }
        }

        #expect(try await org.int("SELECT COUNT(*) FROM domain_events WHERE org_id = \(org.orgId.value)") == 0)
    }

    @Test func `обработчик закрывает все активные участия архивного клиента и идемпотентен`() async throws {
        let org = try await TestOrg.make()
        let client = try await org.client("Анна", archived: true)
        let neighbour = try await org.client("Борис")
        let first = try await org.group("Первая")
        let second = try await org.group("Вторая")
        try await org.enroll(client, first)
        try await org.enroll(client, second)
        try await org.enroll(neighbour, first)
        let handler = LeaveGroupsOnArchive(db: org.db, enrollments: DbEnrollments())
        let event = ClientArchived(clientId: client, archivedBy: org.ctx.employeeId)

        try await handler.clientArchived(org.orgId, event)
        try await handler.clientArchived(org.orgId, event)

        #expect(try await org.activeEnrollments(client) == 0)
        #expect(try await org.activeEnrollments(neighbour) == 1)
        #expect(try await org.int("SELECT COUNT(*) FROM enrollments WHERE client_id = \(client.value)") == 2)
    }

    @Test func `клиент восстановлен до обработки — участия сохраняются`() async throws {
        let org = try await TestOrg.make()
        let client = try await org.client("Анна")
        try await org.enroll(client, try await org.group("Группа"))
        let handler = LeaveGroupsOnArchive(db: org.db, enrollments: DbEnrollments())

        try await handler.clientArchived(org.orgId, ClientArchived(clientId: client, archivedBy: org.ctx.employeeId))

        #expect(try await org.activeEnrollments(client) == 1)
    }

    @Test func `событие чужой организации не трогает клиента`() async throws {
        let org = try await TestOrg.make()
        let other = try await TestOrg.make()
        let client = try await org.client("Анна", archived: true)
        try await org.enroll(client, try await org.group("Группа"))
        let handler = LeaveGroupsOnArchive(db: org.db, enrollments: DbEnrollments())

        try await handler.clientArchived(other.orgId, ClientArchived(clientId: client, archivedBy: org.ctx.employeeId))

        #expect(try await org.activeEnrollments(client) == 1)
    }
}
