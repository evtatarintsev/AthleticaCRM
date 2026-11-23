import Foundation

/// Машинный ключ кастомного поля: только `[a-z_]+`. Создаётся только через [from] (smart constructor).
struct CustomFieldKey: Codable, Hashable, Sendable, CustomStringConvertible {
    /// Значение ключа.
    let value: String

    private init(value: String) { self.value = value }

    /// Ключ из [raw]; нарушение формата — ошибка `INVALID_FIELD_KEY`.
    static func from(_ raw: String) throws(AppError) -> CustomFieldKey {
        guard !raw.isEmpty, raw.allSatisfy({ ("a"..."z").contains($0) || $0 == "_" }) else {
            throw .domain("INVALID_FIELD_KEY", "Ключ поля должен содержать только строчные латинские буквы и символ подчёркивания")
        }
        return CustomFieldKey(value: raw)
    }

    var description: String { value }

    init(from decoder: Decoder) throws {
        self = try CustomFieldKey.from(try decoder.singleValueContainer().decode(String.self))
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(value)
    }
}

/// Типизированное значение кастомного поля. В JSON — объект с дискриминатором `type`.
enum CustomFieldValue: Codable, Equatable, Sendable {
    /// Текстовое значение (для text, phone, email, url).
    case text(CustomFieldKey, String)
    /// Числовое значение.
    case number(CustomFieldKey, Double)
    /// Логическое значение.
    case bool(CustomFieldKey, Bool)
    /// Дата.
    case date(CustomFieldKey, LocalDate)
    /// Значение из списка вариантов.
    case select(CustomFieldKey, String)

    /// Ключ поля, к которому относится значение.
    var fieldKey: CustomFieldKey {
        switch self {
        case let .text(key, _), let .select(key, _): key
        case let .number(key, _): key
        case let .bool(key, _): key
        case let .date(key, _): key
        }
    }

    /// Поле заполнено: строка непустая, прочие типы — всегда.
    var isFilled: Bool {
        switch self {
        case let .text(_, value), let .select(_, value): !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        case .number, .bool, .date: true
        }
    }

    /// Ключи JSON-объекта значения.
    private enum CodingKeys: String, CodingKey { case type, fieldKey, value }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let key = try c.decode(CustomFieldKey.self, forKey: .fieldKey)
        switch try c.decode(String.self, forKey: .type) {
        case "text": self = .text(key, try c.decode(String.self, forKey: .value))
        case "number": self = .number(key, try c.decode(Double.self, forKey: .value))
        case "bool": self = .bool(key, try c.decode(Bool.self, forKey: .value))
        case "date": self = .date(key, try c.decode(LocalDate.self, forKey: .value))
        case "select": self = .select(key, try c.decode(String.self, forKey: .value))
        default: throw DecodingError.dataCorruptedError(forKey: .type, in: c, debugDescription: "Неизвестный тип значения")
        }
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(fieldKey, forKey: .fieldKey)
        switch self {
        case let .text(_, value): try c.encode("text", forKey: .type); try c.encode(value, forKey: .value)
        case let .number(_, value): try c.encode("number", forKey: .type); try c.encode(value, forKey: .value)
        case let .bool(_, value): try c.encode("bool", forKey: .type); try c.encode(value, forKey: .value)
        case let .date(_, value): try c.encode("date", forKey: .type); try c.encode(value, forKey: .value)
        case let .select(_, value): try c.encode("select", forKey: .type); try c.encode(value, forKey: .value)
        }
    }
}

/// Определение кастомного поля организации: тип и ограничения, против которых проверяются значения.
struct CustomFieldDefinition: Sendable, Equatable {
    /// Тип поля с его собственной конфигурацией.
    enum Kind: Sendable, Equatable {
        /// Строка с опциональными границами длины.
        case text(minLength: Int?, maxLength: Int?)
        /// Число с опциональными границами.
        case number(minValue: Int64?, maxValue: Int64?)
        /// Календарная дата.
        case date
        /// Логический флаг.
        case bool
        /// Телефон, email или URL — хранятся строкой без ограничений.
        case textLike
        /// Выбор из фиксированного списка вариантов.
        case select(options: [String])
    }

    /// Ключ поля.
    let fieldKey: CustomFieldKey
    /// Поле обязательно для заполнения.
    let isRequired: Bool
    /// Тип поля.
    let kind: Kind
}

/// Проверка значений кастомных полей по текущим определениям — порт `CustomFieldValues` из `shared`.
struct CustomFieldValues: Sendable {
    /// Определения полей, против которых проверяются значения.
    let definitions: [CustomFieldDefinition]

    /// Проверяет [values] и наличие обязательных полей при создании записи;
    /// повторный ключ заменяет предыдущее значение.
    func forCreation(_ values: [CustomFieldValue]) throws(AppError) -> [CustomFieldValue] {
        var merged: [CustomFieldValue] = []
        for value in values {
            try validate(value)
            if let index = merged.firstIndex(where: { $0.fieldKey == value.fieldKey }) {
                merged[index] = value
            } else {
                merged.append(value)
            }
        }
        for def in definitions where def.isRequired {
            guard merged.first(where: { $0.fieldKey == def.fieldKey })?.isFilled == true else {
                throw Self.requiredField(def.fieldKey)
            }
        }
        return merged
    }

    /// Проверяет полную замену [previous] на [values]: неизменённые значения не перепроверяются
    /// (могут относиться к прежним определениям), обязательное заполненное поле нельзя очистить.
    func forReplacement(previous: [CustomFieldValue], values: [CustomFieldValue]) throws(AppError) -> [CustomFieldValue] {
        let stored = Dictionary(previous.map { ($0.fieldKey, $0) }, uniquingKeysWith: { _, last in last })
        var requested: [CustomFieldKey: CustomFieldValue] = [:]
        var order: [CustomFieldKey] = []
        values.forEach {
            if requested[$0.fieldKey] == nil {
                order.append($0.fieldKey)
            }
            requested[$0.fieldKey] = $0
        }
        for value in values where value != stored[value.fieldKey] {
            try validate(value)
        }
        for def in definitions where def.isRequired {
            if stored[def.fieldKey]?.isFilled == true, requested[def.fieldKey]?.isFilled != true {
                throw Self.requiredField(def.fieldKey)
            }
        }
        return order.compactMap { requested[$0] }
    }

    /// Проверяет, что [value] ссылается на существующее поле подходящего типа и укладывается в ограничения.
    private func validate(_ value: CustomFieldValue) throws(AppError) {
        let key = value.fieldKey
        guard let def = definitions.first(where: { $0.fieldKey == key }) else {
            throw .domain("UNKNOWN_CUSTOM_FIELD", "Неизвестное поле: \(key)")
        }
        switch (def.kind, value) {
        case let (.select(options), .select(_, chosen)):
            if !options.contains(chosen) {
                throw .domain("CUSTOM_FIELD_INVALID_OPTION", "Значение «\(chosen)» не входит в варианты поля \(key)")
            }
        case let (.text(minLength, maxLength), .text(_, text)):
            if let minLength, text.utf16.count < minLength {
                throw .domain("CUSTOM_FIELD_OUT_OF_RANGE", "Недопустимая длина поля \(key)")
            }
            if let maxLength, text.utf16.count > maxLength {
                throw .domain("CUSTOM_FIELD_OUT_OF_RANGE", "Недопустимая длина поля \(key)")
            }
        case let (.number(minValue, maxValue), .number(_, number)):
            let belowMin = minValue.map { number < Double($0) } ?? false
            let aboveMax = maxValue.map { number > Double($0) } ?? false
            if !number.isFinite || belowMin || aboveMax {
                throw .domain("CUSTOM_FIELD_OUT_OF_RANGE", "Значение поля \(key) вне допустимого диапазона")
            }
        case (.textLike, .text), (.date, .date), (.bool, .bool):
            break
        default:
            throw .domain("CUSTOM_FIELD_TYPE_MISMATCH", "Неверный тип для поля \(key)")
        }
    }

    /// Ошибка незаполненного обязательного поля [key].
    private static func requiredField(_ key: CustomFieldKey) -> AppError {
        .domain("CUSTOM_FIELD_REQUIRED", "Заполните обязательное поле \(key)")
    }
}
