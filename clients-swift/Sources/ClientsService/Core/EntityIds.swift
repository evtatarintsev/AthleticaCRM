import Foundation

/// Строго типизированный идентификатор сущности поверх UUID: не даёт перепутать, например,
/// `ClientId` и `GroupId`. В JSON — строка UUID в нижнем регистре, как у Kotlin-сервера.
protocol EntityId: Codable, Hashable, Sendable, CustomStringConvertible {
    /// Значение идентификатора.
    var value: UUID { get }
    /// Создаёт идентификатор из [value].
    init(_ value: UUID)
}

extension EntityId {
    /// Новый идентификатор UUID v7 (упорядочен по времени, как `Uuid.generateV7()` в Kotlin).
    static func new() -> Self { Self(UUID.v7()) }

    /// Строковое представление UUID в нижнем регистре.
    var description: String { value.uuidString.lowercased() }

    init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        guard let uuid = UUID(uuidString: raw) else {
            throw DecodingError.dataCorrupted(.init(codingPath: decoder.codingPath, debugDescription: "Некорректный UUID"))
        }
        self.init(uuid)
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(description)
    }
}

/// Идентификатор клиента.
struct ClientId: EntityId {
    /// Значение идентификатора.
    let value: UUID
    /// Создаёт идентификатор из [value].
    init(_ value: UUID) { self.value = value }
}

/// Идентификатор документа клиента.
struct ClientDocId: EntityId {
    /// Значение идентификатора.
    let value: UUID
    /// Создаёт идентификатор из [value].
    init(_ value: UUID) { self.value = value }
}

/// Идентификатор контакта клиента.
struct ClientContactId: EntityId {
    /// Значение идентификатора.
    let value: UUID
    /// Создаёт идентификатор из [value].
    init(_ value: UUID) { self.value = value }
}

/// Идентификатор группы.
struct GroupId: EntityId {
    /// Значение идентификатора.
    let value: UUID
    /// Создаёт идентификатор из [value].
    init(_ value: UUID) { self.value = value }
}

/// Идентификатор загруженного файла.
struct UploadId: EntityId {
    /// Значение идентификатора.
    let value: UUID
    /// Создаёт идентификатор из [value].
    init(_ value: UUID) { self.value = value }
}

/// Идентификатор источника клиента.
struct LeadSourceId: EntityId {
    /// Значение идентификатора.
    let value: UUID
    /// Создаёт идентификатор из [value].
    init(_ value: UUID) { self.value = value }
}

/// Идентификатор абонемента.
struct MembershipId: EntityId {
    /// Значение идентификатора.
    let value: UUID
    /// Создаёт идентификатор из [value].
    init(_ value: UUID) { self.value = value }
}

/// Идентификатор организации.
struct OrgId: EntityId {
    /// Значение идентификатора.
    let value: UUID
    /// Создаёт идентификатор из [value].
    init(_ value: UUID) { self.value = value }
}

/// Идентификатор пользователя.
struct UserId: EntityId {
    /// Значение идентификатора.
    let value: UUID
    /// Создаёт идентификатор из [value].
    init(_ value: UUID) { self.value = value }
}

/// Идентификатор сотрудника.
struct EmployeeId: EntityId {
    /// Значение идентификатора.
    let value: UUID
    /// Создаёт идентификатор из [value].
    init(_ value: UUID) { self.value = value }
}

/// Идентификатор филиала.
struct BranchId: EntityId {
    /// Значение идентификатора.
    let value: UUID
    /// Создаёт идентификатор из [value].
    init(_ value: UUID) { self.value = value }
}

/// Идентификатор сессии входа.
struct AuthSessionId: EntityId {
    /// Значение идентификатора.
    let value: UUID
    /// Создаёт идентификатор из [value].
    init(_ value: UUID) { self.value = value }
}

extension UUID {
    /// Новый UUID версии 7 (RFC 9562): 48 бит миллисекунд Unix-времени, затем случайные биты.
    static func v7() -> UUID {
        let millis = UInt64(Date().timeIntervalSince1970 * 1000)
        var bytes = (0..<16).map { _ in UInt8.random(in: 0...255) }
        (0..<6).forEach { bytes[$0] = UInt8(truncatingIfNeeded: millis >> (8 * (5 - UInt64($0)))) }
        bytes[6] = (bytes[6] & 0x0F) | 0x70
        bytes[8] = (bytes[8] & 0x3F) | 0x80
        return UUID(uuid: (
            bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5], bytes[6], bytes[7],
            bytes[8], bytes[9], bytes[10], bytes[11], bytes[12], bytes[13], bytes[14], bytes[15]
        ))
    }
}

/// Идентификатор заметки о клиенте.
struct ClientNoteId: EntityId {
    /// Значение идентификатора.
    let value: UUID
    /// Создаёт идентификатор из [value].
    init(_ value: UUID) { self.value = value }
}

/// Идентификатор занятия.
struct SessionId: EntityId {
    /// Значение идентификатора.
    let value: UUID
    /// Создаёт идентификатор из [value].
    init(_ value: UUID) { self.value = value }
}

/// Идентификатор записи доменного события в outbox-таблице `domain_events`.
struct DomainEventId: EntityId {
    /// Значение идентификатора.
    let value: UUID
    /// Создаёт идентификатор из [value].
    init(_ value: UUID) { self.value = value }
}
