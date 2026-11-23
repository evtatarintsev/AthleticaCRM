import Foundation
import PostgresNIO

/// SQL с именованными параметрами `:name`, как `QueryBuilder` Kotlin-сервера: один параметр
/// можно использовать в тексте многократно. Нужен там, где запрос собирается из фрагментов
/// (проекция списка клиентов); простые запросы пишутся интерполяцией [PostgresQuery].
/// Приведение типа `::type` параметром не считается. Значение передаётся bind-параметром.
struct NamedQuery: Sendable {
    /// Текст запроса с именованными параметрами.
    let sql: String
    /// Значения параметров по имени; `nil` — SQL NULL.
    private var values: [String: (any PostgresDynamicTypeEncodable & Sendable)?] = [:]

    /// Запрос с текстом [sql].
    init(_ sql: String) { self.sql = sql }

    /// Копия с параметром [name] = [value].
    func bind(_ name: String, _ value: (some PostgresDynamicTypeEncodable & Sendable)?) -> NamedQuery {
        var copy = self
        // updateValue, а не присваивание: присваивание nil удалило бы ключ вместо привязки NULL.
        copy.values.updateValue(value.map { $0 as any PostgresDynamicTypeEncodable & Sendable }, forKey: name)
        return copy
    }

    /// Запрос postgres-nio с позиционными параметрами `$n`; параметр без значения — ошибка программиста.
    func compiled() throws(AppError) -> PostgresQuery {
        let pattern = /::|:([A-Za-z][A-Za-z0-9]*)/
        var text = ""
        var bindings = PostgresBindings()
        var positions: [String: Int] = [:]
        var rest = Substring(sql)
        while let match = rest.firstMatch(of: pattern) {
            text += rest[rest.startIndex..<match.range.lowerBound]
            rest = rest[match.range.upperBound...]
            guard let captured = match.output.1 else {
                text += "::"
                continue
            }
            let name = String(captured)
            guard let value = values[name] else {
                throw .infrastructure("Параметр :\(name) не привязан")
            }
            if positions[name] == nil {
                positions[name] = positions.count + 1
                if let value {
                    bindings.append(value)
                } else {
                    bindings.appendNull()
                }
            }
            text += "$\(positions[name] ?? 0)"
        }
        text += rest
        return PostgresQuery(unsafeSQL: text, binds: bindings)
    }
}

extension Money {
    /// Сумма из десятичной записи [text] колонки `NUMERIC` (основные единицы) в валюте [currency].
    static func fromDb(_ text: String, _ currency: Currency) throws(AppError) -> Money {
        guard let amount = Decimal(string: text, locale: Locale(identifier: "en_US_POSIX")) else {
            throw .infrastructure("Некорректная сумма из БД: \(text)")
        }
        var scaled = amount * pow(10, currency.fractionDigits)
        var rounded = Decimal()
        NSDecimalRound(&rounded, &scaled, 0, .plain)
        return Money(minorUnits: NSDecimalNumber(decimal: rounded).int64Value, currency: currency)
    }
}
