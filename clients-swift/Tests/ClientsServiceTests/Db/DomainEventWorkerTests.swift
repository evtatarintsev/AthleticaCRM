import Foundation
import Logging
import Synchronization
import Testing
@testable import ClientsService

/// Тестовое событие воркера: другие тесты его не публикуют.
private struct WorkerProbe: DomainEvent {
    static let eventType = "WorkerProbe"
    /// Метка события.
    let marker: String
}

/// Счётчик вызовов обработчика и переключатель сбоя.
private final class Probe: Sendable {
    /// Число вызовов.
    let calls = Atomic(0)
    /// Падает ли обработчик.
    let failing = Atomic(true)
}

/// DB-тесты воркера доменных событий — порт `DomainEventWorkerTest.kt`. Последовательно: воркер
/// выбирает события всех организаций, а тесты делят тип [WorkerProbe].
@Suite(.serialized) struct DomainEventWorkerTests {
    /// Строка outbox-таблицы для проверок.
    private struct Row: Equatable {
        let retryCount: Int, processed: Bool, failed: Bool, delayedSeconds: Int, lastError: String?
    }

    /// Организация, воркер с обработчиком [WorkerProbe] и счётчик вызовов.
    private func setUp() async throws -> (TestOrg, DomainEventWorker, Probe) {
        let org = try await TestOrg.make()
        try await org.sql("DELETE FROM domain_events WHERE event_type = 'WorkerProbe'")
        let probe = Probe()
        let bus = DomainEventBus().on(WorkerProbe.self) { _, _ throws(AppError) in
            probe.calls.add(1, ordering: .relaxed)
            if probe.failing.load(ordering: .relaxed) {
                throw .infrastructure("временный сбой")
            }
        }
        return (org, DomainEventWorker(db: org.db, bus: bus, logger: Logger(label: "test-worker")), probe)
    }

    /// Публикует [WorkerProbe] через шину и возвращает идентификатор записи.
    private func publish(_ org: TestOrg) async throws -> UUID {
        try await org.tx { ctx, tr throws(AppError) in
            try await DomainEventBus().publish(WorkerProbe(marker: "m"), ctx: ctx, tr: tr)
            return try await tr.first("SELECT id FROM domain_events WHERE org_id = \(ctx.orgId.value) ORDER BY created_at DESC LIMIT 1") {
                try $0.decode(UUID.self)
            } ?? UUID()
        }
    }

    /// Состояние записи [id].
    private func row(_ org: TestOrg, _ id: UUID) async throws -> Row? {
        try await org.tx { _, tr throws(AppError) in
            try await tr.first("""
                SELECT retry_count, processed_at IS NOT NULL, failed_at IS NOT NULL,
                       EXTRACT(EPOCH FROM next_attempt_at - now())::int, last_error
                FROM domain_events WHERE id = \(id)
                """) { row in
                let (retry, processed, failed, delayed, error) = try row.decode((Int, Bool, Bool, Int, String?).self)
                return Row(retryCount: retry, processed: processed, failed: failed, delayedSeconds: delayed, lastError: error)
            }
        }
    }

    @Test func `задержка растёт вдвое и ограничена часом`() {
        #expect(DomainEventWorker.retryDelay(0) == .seconds(10))
        #expect(DomainEventWorker.retryDelay(1) == .seconds(20))
        #expect(DomainEventWorker.retryDelay(8) == .seconds(2560))
        #expect(DomainEventWorker.retryDelay(9) == .seconds(3600))
        #expect(DomainEventWorker.retryDelay(40) == .seconds(3600))
    }

    @Test func `payload несёт дискриминатор type, как у Kotlin`() async throws {
        let (org, _, _) = try await setUp()
        let id = try await publish(org)
        let matches = try await org.int("""
            SELECT COUNT(*) FROM domain_events WHERE id = \(id) AND payload = '{"type": "WorkerProbe", "marker": "m"}'::jsonb
            """)
        #expect(matches == 1)
    }

    @Test func `после ошибки событие откладывается, а не повторяется на следующем опросе`() async throws {
        let (org, worker, probe) = try await setUp()
        let id = try await publish(org)

        #expect(try await worker.processBatch() == 1)
        #expect(try await worker.processBatch() == 0)

        #expect(probe.calls.load(ordering: .relaxed) == 1)
        let after = try #require(try await row(org, id))
        #expect(after.retryCount == 1)
        #expect((5...10).contains(after.delayedSeconds))
        #expect(!after.processed)
        #expect(after.lastError?.contains("временный сбой") == true)
    }

    @Test func `исчерпанные повторы — событие неуспешное и больше не выбирается`() async throws {
        let (org, worker, probe) = try await setUp()
        let id = try await publish(org)
        try await org.sql("UPDATE domain_events SET retry_count = 11 WHERE id = \(id)")

        try await worker.processBatch()
        let failed = try #require(try await row(org, id))
        #expect(failed.failed && !failed.processed)

        try await org.sql("UPDATE domain_events SET next_attempt_at = now() WHERE id = \(id)")
        try await worker.processBatch()
        #expect(probe.calls.load(ordering: .relaxed) == 1)
    }

    @Test func `успешная обработка помечает событие выполненным`() async throws {
        let (org, worker, probe) = try await setUp()
        probe.failing.store(false, ordering: .relaxed)
        let id = try await publish(org)

        try await worker.processBatch()

        let after = try #require(try await row(org, id))
        #expect(after.processed && after.lastError == nil)
    }

    @Test func `события без обработчика на этой шине не выбираются`() async throws {
        let (org, worker, probe) = try await setUp()
        let id = try await publish(org)
        try await org.sql("UPDATE domain_events SET event_type = 'SessionCancelled' WHERE id = \(id)")

        #expect(try await worker.processBatch() == 0)

        #expect(probe.calls.load(ordering: .relaxed) == 0)
        #expect(try await row(org, id)?.processed == false)
        try await org.sql("DELETE FROM domain_events WHERE id = \(id)")
    }

    @Test func `цикл run обрабатывает события и останавливается отменой задачи`() async throws {
        let (org, worker, probe) = try await setUp()
        probe.failing.store(false, ordering: .relaxed)
        let id = try await publish(org)
        var fast = worker
        fast.pollingInterval = .milliseconds(20)

        let task = Task { [fast] in await fast.run() }
        for _ in 0..<100 where try await row(org, id)?.processed != true {
            try await Task.sleep(for: .milliseconds(20))
        }
        task.cancel()
        await task.value

        #expect(try await row(org, id)?.processed == true)
    }
}
