import Logging
import PostgresNIO

/// Пул соединений PostgreSQL. Единственная точка открытия транзакций.
struct Database: Sendable {
    /// Клиент postgres-nio с пулом соединений.
    let client: PostgresClient
    /// Логгер запросов.
    let logger: Logger

    /// Выполняет [body] в транзакции: коммит при успехе, откат при любой ошибке.
    /// Ошибки драйвера, не ставшие [AppError] внутри [body], превращаются в `AppError.infrastructure`.
    func transaction<T: Sendable>(_ body: (Transaction) async throws(AppError) -> T) async throws(AppError) -> T {
        do {
            return try await client.withTransaction(logger: logger) { connection in
                try await body(Transaction(connection: connection, logger: logger))
            }
        } catch let error as AppError {
            throw error
        } catch let error as PostgresTransactionError {
            if let closureError = error.closureError as? AppError {
                throw closureError
            }
            throw .infrastructure(String(reflecting: error))
        } catch {
            throw .infrastructure(String(reflecting: error))
        }
    }
}

/// Открытая транзакция: выполняет SQL с параметрами, подставленными через интерполяцию
/// [PostgresQuery] (значения уходят отдельными bind-параметрами, а не текстом запроса).
/// Нарушение ограничения БД поднимается как `AppError.constraintViolation(имя)`, если оно не
/// перечислено в `onViolation` — тогда как соответствующая доменная ошибка.
struct Transaction: @unchecked Sendable {
    /// Соединение, на котором открыта транзакция.
    let connection: PostgresConnection
    /// Логгер запросов.
    let logger: Logger

    /// Строки результата [query], преобразованные [map].
    func list<T>(
        _ query: PostgresQuery,
        onViolation: [String: DomainError] = [:],
        _ map: (PostgresRow) throws -> T
    ) async throws(AppError) -> [T] {
        do {
            var result: [T] = []
            for try await row in try await connection.query(query, logger: logger) {
                result.append(try map(row))
            }
            return result
        } catch {
            throw Self.appError(error, onViolation)
        }
    }

    /// Первая строка результата [query], преобразованная [map]; `nil`, если строк нет.
    func first<T>(
        _ query: PostgresQuery,
        onViolation: [String: DomainError] = [:],
        _ map: (PostgresRow) throws -> T
    ) async throws(AppError) -> T? {
        try await list(query, onViolation: onViolation, map).first
    }

    /// Выполняет [query] и возвращает число строк результата; для счёта изменённых строк
    /// команда должна заканчиваться `RETURNING 1`.
    @discardableResult
    func execute(_ query: PostgresQuery, onViolation: [String: DomainError] = [:]) async throws(AppError) -> Int {
        try await list(query, onViolation: onViolation) { _ in () }.count
    }

    /// Переводит ошибку драйвера в [AppError]: известное нарушение ограничения — в доменную ошибку из [onViolation].
    private static func appError(_ error: any Error, _ onViolation: [String: DomainError]) -> AppError {
        if let error = error as? AppError {
            return error
        }
        if let error = error as? PSQLError,
           let state = error.serverInfo?[.sqlState], state.hasPrefix("23"),
           let constraint = error.serverInfo?[.constraintName]
        {
            return onViolation[constraint].map(AppError.domain) ?? .constraintViolation(constraint)
        }
        return .infrastructure(String(reflecting: error))
    }
}
