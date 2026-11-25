import Foundation
import Logging
import PostgresNIO

/// Доменное событие: хранится в outbox-таблице `domain_events`. Имя типа [eventType] пишется
/// в `event_type` и дискриминатором `type` в payload — тот же формат, что у Kotlin-сервера.
protocol DomainEvent: Codable, Sendable {
    /// Имя типа события.
    static var eventType: String { get }
}

/// Клиент переведён в архив.
struct ClientArchived: DomainEvent {
    static let eventType = "ClientArchived"
    /// Клиент.
    let clientId: ClientId
    /// Сотрудник, архивировавший клиента.
    let archivedBy: EmployeeId
}

/// Публикация доменных событий.
protocol DomainEvents: Sendable {
    /// Записывает [event] организации из [ctx] в outbox в транзакции [tr]; обработчики
    /// вызовет воркер после коммита.
    func publish(_ event: some DomainEvent, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError)
}

/// Запись outbox-таблицы, выбранная воркером.
struct DomainEventRecord: Sendable {
    /// Идентификатор записи.
    let id: DomainEventId
    /// Организация, в которой произошло событие.
    let orgId: OrgId
    /// Имя типа события.
    let eventType: String
    /// JSON-тело события с дискриминатором `type`.
    let payload: String
    /// Число неудачных попыток обработки.
    let retryCount: Int
}

/// Обработчик записи события; ошибка — временный сбой, воркер повторит обработку.
typealias DomainEventDispatch = @Sendable (DomainEventRecord) async throws(AppError) -> Void

/// Шина доменных событий (outbox): [publish] пишет событие в `domain_events` в текущей транзакции,
/// [dispatch] передаёт выбранную воркером запись обработчикам её типа. Обработчики регистрируются
/// через [on] при сборке зависимостей, дальше шина неизменяема.
struct DomainEventBus: DomainEvents {
    /// Обработчики по имени типа события.
    private var handlers: [String: [DomainEventDispatch]] = [:]

    /// Пустая шина без обработчиков.
    init() {}

    /// Типы событий, у которых есть обработчики: только их выбирает воркер этого сервиса.
    var eventTypes: [String] { handlers.keys.sorted() }

    /// Копия шины с обработчиком [handler] событий типа [type]; у типа может быть несколько обработчиков.
    func on<E: DomainEvent>(_ type: E.Type, _ handler: @escaping @Sendable (OrgId, E) async throws(AppError) -> Void) -> DomainEventBus {
        let dispatch: DomainEventDispatch = { record throws(AppError) in
            let event = try decodeJson(E.self, record.payload)
            try await handler(record.orgId, event)
        }
        var copy = self
        copy.handlers[E.eventType, default: []].append(dispatch)
        return copy
    }

    func publish(_ event: some DomainEvent, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) {
        let payload = try jsonString(Envelope(event: event))
        try await tr.execute("""
            INSERT INTO domain_events (id, org_id, event_type, payload)
            VALUES (\(UUID.v7()), \(ctx.orgId.value), \(type(of: event).eventType), \(payload)::jsonb)
            """)
    }

    /// Передаёт [record] всем обработчикам его типа; ошибка любого — ошибка всей записи.
    /// Обработчиков нет — запись пропускается: воркер выбирает только типы из [eventTypes].
    func dispatch(_ record: DomainEventRecord) async throws(AppError) {
        for handler in handlers[record.eventType] ?? [] {
            try await handler(record)
        }
    }
}

/// Событие с дискриминатором `type` на верхнем уровне JSON, как у kotlinx.serialization.
private struct Envelope<E: DomainEvent>: Encodable {
    /// Событие.
    let event: E

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: TypeKey.self)
        try container.encode(E.eventType, forKey: .type)
        try event.encode(to: encoder)
    }

    /// Ключ дискриминатора.
    private enum TypeKey: String, CodingKey { case type }
}
