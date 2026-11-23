import Foundation
import PostgresNIO

/// Контакт клиента: значение [value] типа [type] (телефон, email, Telegram и т.п.).
struct ClientContact: Equatable, Sendable {
    /// Идентификатор контакта.
    let id: ClientContactId
    /// Клиент, которому принадлежит контакт.
    let clientId: ClientId
    /// Тип контакта.
    let type: ContactType
    /// Значение: номер, адрес, имя пользователя.
    let value: String
    /// Комментарий, например «мама»; `nil` — без комментария.
    var label: String?
    /// Основной контакт своего типа.
    var isPrimary: Bool
}

/// Максимальная длина комментария к контакту после обрезки пробелов.
let maxContactLabelLength = 100

extension Array where Element == ClientContact {
    /// Набор контактов в том виде, в котором он хранится: комментарии без пробелов по краям
    /// (пустой — `nil`), ровно один основной в каждом типе (если не отмечен — первый контакт типа),
    /// контакты одного типа подряд в порядке появления типа, основной — первым в типе.
    /// Два основных в одном типе — `CONTACT_PRIMARY_DUPLICATE`, длинный комментарий — `CONTACT_LABEL_TOO_LONG`.
    func normalized() throws(AppError) -> [ClientContact] {
        var typeOrder: [ContactType] = []
        var byType: [ContactType: [Int]] = [:]
        indices.forEach {
            if byType[self[$0].type] == nil {
                typeOrder.append(self[$0].type)
            }
            byType[self[$0].type, default: []].append($0)
        }
        var result: [ClientContact] = []
        for type in typeOrder {
            let sameType = byType[type] ?? []
            guard sameType.filter({ self[$0].isPrimary }).count <= 1 else {
                throw .domain("CONTACT_PRIMARY_DUPLICATE", "У клиента может быть только один основной контакт каждого типа")
            }
            let primary = sameType.first { self[$0].isPrimary } ?? sameType[0]
            for index in [primary] + sameType.filter({ $0 != primary }) {
                var contact = self[index]
                let label = contact.label?.trimmingCharacters(in: .whitespacesAndNewlines)
                contact.label = (label?.isEmpty ?? true) ? nil : label
                if let label = contact.label, label.utf16.count > maxContactLabelLength {
                    throw .domain("CONTACT_LABEL_TOO_LONG", "Комментарий к контакту длиннее \(maxContactLabelLength) символов")
                }
                contact.isPrimary = index == primary
                result.append(contact)
            }
        }
        return result
    }
}

/// Репозиторий контактов клиентов; все операции изолированы по организации.
protocol ClientContacts: Sendable {
    /// Контакты клиента [clientId] в порядке хранения.
    func byClient(_ clientId: ClientId, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) -> [ClientContact]

    /// Полностью заменяет контакты клиента [client] на [contacts], приведённые к [normalized].
    func replace(_ client: any ActiveClient, _ contacts: [ClientContact], ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError)
}

/// Контакты клиентов поверх PostgreSQL.
struct DbClientContacts: ClientContacts {
    func byClient(_ clientId: ClientId, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) -> [ClientContact] {
        try await tr.list("""
            SELECT id, type, value, label, is_primary
            FROM client_contacts
            WHERE client_id = \(clientId.value) AND org_id = \(ctx.orgId.value)
            ORDER BY position
            """) { row in
            let (id, type, value, label, isPrimary) = try row.decode((UUID, String, String, String?, Bool).self)
            return ClientContact(
                id: ClientContactId(id),
                clientId: clientId,
                type: ContactType(rawValue: type) ?? .PHONE,
                value: value,
                label: label,
                isPrimary: isPrimary
            )
        }
    }

    func replace(_ client: any ActiveClient, _ contacts: [ClientContact], ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) {
        try await client.lockForWrite(ctx: ctx, tr: tr)
        let stored = try contacts.normalized()
        try await tr.execute("DELETE FROM client_contacts WHERE client_id = \(client.data.id.value) AND org_id = \(ctx.orgId.value)")
        for (position, contact) in stored.enumerated() {
            try await tr.execute("""
                INSERT INTO client_contacts (id, org_id, client_id, type, value, label, is_primary, position)
                VALUES (\(contact.id.value), \(ctx.orgId.value), \(client.data.id.value), \(contact.type.rawValue),
                        \(contact.value), \(contact.label), \(contact.isPrimary), \(position))
                """)
        }
    }
}
