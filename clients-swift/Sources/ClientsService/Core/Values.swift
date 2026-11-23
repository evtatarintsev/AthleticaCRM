import Foundation

/// Валюта организации (ISO 4217). Набор фиксирован, как `Currency` в `shared`.
enum Currency: String, Codable, Sendable {
    case RUB, USD, EUR, KZT, BYN, UAH

    /// Количество дробных разрядов — сколько минорных единиц в одной основной.
    var fractionDigits: Int { 2 }
}

/// Денежная сумма в минорных единицах и валюте — единственное представление денег в коде.
struct Money: Codable, Equatable, Sendable {
    /// Сумма в минорных единицах (копейки/центы); может быть отрицательной.
    let minorUnits: Int64
    /// Валюта суммы.
    let currency: Currency
}

/// Календарная дата без времени (`kotlinx.datetime.LocalDate`); в JSON и SQL — `YYYY-MM-DD`.
struct LocalDate: Codable, Comparable, Hashable, Sendable, CustomStringConvertible {
    /// Год.
    let year: Int
    /// Месяц, 1–12.
    let month: Int
    /// День месяца, 1–31.
    let day: Int

    /// Разбирает [text] формата `YYYY-MM-DD`; `nil`, если такой даты не существует.
    static func parse(_ text: String) -> LocalDate? {
        let parts = text.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3, text.count == 10 else {
            return nil
        }
        let date = LocalDate(year: parts[0], month: parts[1], day: parts[2])
        guard let restored = Calendar.utc.date(from: date.components), LocalDate(restored, in: .utc) == date else {
            return nil
        }
        return date
    }

    /// Дата момента [instant] в часовом поясе [timeZone].
    init(_ instant: Date, in timeZone: TimeZone) {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let parts = calendar.dateComponents([.year, .month, .day], from: instant)
        self.init(year: parts.year ?? 0, month: parts.month ?? 0, day: parts.day ?? 0)
    }

    /// Создаёт дату из года [year], месяца [month] и дня [day] без проверки.
    init(year: Int, month: Int, day: Int) {
        self.year = year
        self.month = month
        self.day = day
    }

    /// Компоненты даты для календарной арифметики.
    private var components: DateComponents { DateComponents(year: year, month: month, day: day) }

    /// Число дней от этой даты до [other]; отрицательное, если [other] раньше.
    func daysUntil(_ other: LocalDate) -> Int {
        let from = Calendar.utc.date(from: components) ?? .distantPast
        let to = Calendar.utc.date(from: other.components) ?? .distantPast
        return Calendar.utc.dateComponents([.day], from: from, to: to).day ?? 0
    }

    /// День рождения как целое MMDD (месяц × 100 + день) для сравнения без учёта года.
    var monthDay: Int { month * 100 + day }

    var description: String { String(format: "%04d-%02d-%02d", year, month, day) }

    static func < (lhs: LocalDate, rhs: LocalDate) -> Bool {
        (lhs.year, lhs.month, lhs.day) < (rhs.year, rhs.month, rhs.day)
    }

    init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        guard let date = LocalDate.parse(raw) else {
            throw DecodingError.dataCorrupted(.init(codingPath: decoder.codingPath, debugDescription: "Некорректная дата"))
        }
        self = date
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(description)
    }
}

extension Calendar {
    /// Григорианский календарь в UTC — для арифметики над [LocalDate].
    static let utc: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .utc
        return calendar
    }()
}

extension TimeZone {
    /// Часовой пояс UTC.
    static let utc = TimeZone(identifier: "UTC") ?? .current
}

/// Диапазон дат включительно с обеих сторон.
struct DateRange: Codable, Equatable, Sendable {
    /// Начало диапазона (включительно).
    let from: LocalDate
    /// Конец диапазона (включительно).
    let to: LocalDate
}

/// Пол клиента.
enum Gender: String, Codable, Sendable {
    case MALE, FEMALE
}

/// Тип контакта клиента: чем является значение (номер телефона, email, имя пользователя).
enum ContactType: String, Codable, Sendable, Hashable {
    case PHONE, EMAIL, TELEGRAM, VK, FACEBOOK
}

/// Непустое имя без пробелов по краям. Инвариант проверяется при создании (smart constructor),
/// в том числе на десериализации — как `NonBlankName` в `shared`.
struct NonBlankName: Codable, Equatable, Sendable {
    /// Нормализованное имя.
    let value: String

    private init(value: String) { self.value = value }

    /// Имя из [raw] без пробелов по краям; пустое — ошибка `NAME_REQUIRED`.
    static func from(_ raw: String) throws(AppError) -> NonBlankName {
        let normalized = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalized.isEmpty else {
            throw .domain("NAME_REQUIRED", "Имя или заголовок не может быть пустым")
        }
        return NonBlankName(value: normalized)
    }

    init(from decoder: Decoder) throws {
        self = try NonBlankName.from(try decoder.singleValueContainer().decode(String.self))
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(value)
    }
}

/// Значение, которое в JSON всегда присутствует: `nil` кодируется явным `null`.
/// Синтезированный `Encodable` пропускает `nil`-поля, а контракты веб-клиента требуют ключ
/// (`.nullable()`, не `.optional()`), как отдаёт kotlinx.serialization с `encodeDefaults`.
@propertyWrapper
struct Nullable<T: Codable & Sendable & Equatable>: Codable, Sendable, Equatable {
    /// Обёрнутое значение.
    var wrappedValue: T?

    /// Оборачивает [wrappedValue].
    init(wrappedValue: T?) { self.wrappedValue = wrappedValue }

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        wrappedValue = container.decodeNil() ? nil : try container.decode(T.self)
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        if let value = wrappedValue {
            try container.encode(value)
        } else {
            try container.encodeNil()
        }
    }
}
