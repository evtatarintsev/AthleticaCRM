import Foundation
import PostgresNIO

/// Тип действия в журнале аудита (подмножество `AuditActionType`, нужное клиентскому API).
enum AuditActionType: String, Sendable {
    case create, update, delete
}

/// Событие журнала аудита от имени сотрудника [ctx].
struct AuditEvent: Sendable {
    /// Контекст сотрудника: организация, пользователь, IP.
    let ctx: EmployeeRequestContext
    /// Тип действия.
    let actionType: AuditActionType
    /// Тип сущности, например `client`, `client_doc`, `enrollment`.
    let entityType: String
    /// Идентификатор сущности.
    let entityId: UUID
    /// Дополнительные данные события — JSON-строка.
    let data: String
}

/// Журнал аудита бизнес-изменений; отделён от хранилища интерфейсом.
protocol AuditLog: Sendable {
    /// Записывает [event] в транзакции [tr].
    func log(_ event: AuditEvent, tr: Transaction) async throws(AppError)
}

/// Журнал аудита в таблице `audit_logs`.
struct PostgresAuditLog: AuditLog {
    func log(_ event: AuditEvent, tr: Transaction) async throws(AppError) {
        let ctx = event.ctx
        try await tr.execute("""
            INSERT INTO audit_logs (org_id, user_id, username, action_type, entity_type, entity_id, data, ip_address)
            VALUES (\(ctx.orgId.value), \(ctx.userId.value), \(ctx.username), \(event.actionType.rawValue),
                    \(event.entityType), \(event.entityId), \(event.data)::jsonb, \(ctx.clientIp))
            """)
    }
}

/// Кодировщик JSON для данных аудита и JSONB-колонок: компактный, с детерминированным порядком ключей.
let compactJson: JSONEncoder = {
    let encoder = JSONEncoder()
    encoder.outputFormatting = [.sortedKeys, .withoutEscapingSlashes]
    return encoder
}()

/// JSON-строка значения [value]; ошибка кодирования — ошибка программиста, поэтому `infrastructure`.
func jsonString(_ value: some Encodable) throws(AppError) -> String {
    do {
        return String(decoding: try compactJson.encode(value), as: UTF8.self)
    } catch {
        throw .infrastructure("Не удалось сериализовать JSON: \(error)")
    }
}

/// Значение типа [type] из JSON-строки [text] (например, JSONB-колонки).
func decodeJson<T: Decodable>(_ type: T.Type, _ text: String) throws(AppError) -> T {
    do {
        return try JSONDecoder().decode(type, from: Data(text.utf8))
    } catch {
        throw .infrastructure("Не удалось разобрать JSON: \(error)")
    }
}
