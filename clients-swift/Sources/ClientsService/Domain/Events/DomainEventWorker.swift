import Foundation
import Logging
import PostgresNIO

/// Воркер outbox-таблицы `domain_events` — порт `DomainEventWorker.kt`.
///
/// - At-least-once: сбой после обработки, но до пометки — событие повторится.
/// - После ошибки событие откладывается на [retryDelay]; после [maxAttempts] неудачных попыток
///   помечается `failed_at` и ждёт `admin events retry-failed` на Kotlin-сервере.
/// - Обработчики вызываются вне транзакции выборки и открывают свои транзакции.
/// - Выбираются только типы событий, у которых есть обработчики на шине [bus]: таблицу делят
///   с Kotlin-сервером, и чужие события не трогаются.
///
/// Пока пачки полные, следующая выбирается сразу; неполная — пауза [pollingInterval].
struct DomainEventWorker: Sendable {
    /// Сколько событий выбирается за итерацию.
    static let batchSize = 10
    /// Сколько попыток даётся событию до пометки неуспешным.
    static let maxAttempts = 12

    /// База данных.
    let db: Database
    /// Шина с обработчиками.
    let bus: DomainEventBus
    /// Пауза после неполной пачки.
    var pollingInterval: Duration = .seconds(1)
    /// Логгер.
    let logger: Logger

    /// Цикл опроса до отмены задачи; ошибка итерации пишется в лог и не останавливает цикл.
    func run() async {
        logger.info("Воркер доменных событий запущен: \(bus.eventTypes)")
        while !Task.isCancelled {
            let processed: Int
            do {
                processed = try await processBatch()
            } catch {
                logger.error("Ошибка в цикле обработки событий: \(error)")
                processed = 0
            }
            if processed < Self.batchSize {
                try? await Task.sleep(for: pollingInterval)
            }
        }
        logger.info("Воркер доменных событий остановлен")
    }

    /// Обрабатывает одну пачку событий, чья очередная попытка наступила; возвращает размер пачки.
    @discardableResult
    func processBatch() async throws(AppError) -> Int {
        let records = try await db.transaction { tr throws(AppError) in try await fetchPending(tr) }
        for record in records {
            do {
                try await bus.dispatch(record)
                try await db.transaction { tr throws(AppError) in
                    try await tr.execute("UPDATE domain_events SET processed_at = now() WHERE id = \(record.id.value)")
                }
            } catch {
                logger.error("Ошибка обработки события \(record.id) (\(record.eventType)): \(error)")
                try await db.transaction { tr throws(AppError) in try await registerFailure(record, String(describing: error), tr) }
            }
        }
        return records.count
    }

    /// Пачка необработанных событий своих типов, чья попытка наступила.
    private func fetchPending(_ tr: Transaction) async throws(AppError) -> [DomainEventRecord] {
        let rows = try await tr.list("""
            SELECT id, org_id, event_type, payload::text, retry_count
            FROM domain_events
            WHERE processed_at IS NULL AND failed_at IS NULL AND next_attempt_at <= now()
              AND event_type = ANY(\(bus.eventTypes))
            ORDER BY created_at
            LIMIT \(Self.batchSize)
            FOR UPDATE SKIP LOCKED
            """) { row in try row.decode((UUID, UUID, String, String, Int).self) }
        return rows.map { id, orgId, eventType, payload, retryCount in
            DomainEventRecord(id: DomainEventId(id), orgId: OrgId(orgId), eventType: eventType, payload: payload, retryCount: retryCount)
        }
    }

    /// Учитывает неудачную попытку [record] с ошибкой [error]: следующая попытка через [retryDelay]
    /// или, если попыток стало [maxAttempts], пометка неуспешным.
    private func registerFailure(_ record: DomainEventRecord, _ error: String, _ tr: Transaction) async throws(AppError) {
        let attempts = record.retryCount + 1
        if attempts >= Self.maxAttempts {
            logger.error("Событие \(record.id) (\(record.eventType)) исчерпало \(Self.maxAttempts) попыток и помечено неуспешным")
        }
        let delaySeconds = Double(Self.retryDelay(record.retryCount).components.seconds)
        try await tr.execute("""
            UPDATE domain_events
            SET retry_count = \(attempts),
                last_error = \(error),
                next_attempt_at = now() + make_interval(secs => \(delaySeconds)),
                failed_at = CASE WHEN \(attempts) >= \(Self.maxAttempts) THEN now() END
            WHERE id = \(record.id.value)
            """)
    }

    /// Задержка перед следующей попыткой после [failedAttempts] неудачных: 10 с · 2^n, но не больше часа.
    static func retryDelay(_ failedAttempts: Int) -> Duration {
        let maxDelay = Duration.seconds(3600)
        if failedAttempts >= 20 {
            return maxDelay
        }
        return min(.seconds(10 * (1 << failedAttempts)), maxDelay)
    }
}
