/// Бизнес-ошибка: маршрут отвечает HTTP 400 с её [code] и [message].
/// Аналог `CommonDomainError` Kotlin-сервера.
struct DomainError: Error, Equatable, Sendable {
    /// Машиночитаемый код ошибки в формате SCREAMING_SNAKE_CASE.
    let code: String
    /// Человекочитаемое описание ошибки.
    let message: String
}

/// Любая ожидаемая ошибка обработки запроса — аналог `AppError` из `core/errors`.
/// Бизнес-логика бросает только её (typed throws `throws(AppError)`), а маршрут переводит в HTTP-ответ.
enum AppError: Error, Equatable, Sendable {
    /// Бизнес-ошибка — HTTP 400.
    case domain(DomainError)
    /// Токен недействителен, сессия завершена или сотрудник заблокирован — HTTP 401.
    case unauthorized
    /// Нарушение ограничения БД, не переведённое в доменную ошибку — HTTP 500.
    case constraintViolation(String)
    /// Ошибка инфраструктуры (соединение, синтаксис SQL) — HTTP 500.
    case infrastructure(String)

    /// Бизнес-ошибка с кодом [code] и сообщением [message].
    static func domain(_ code: String, _ message: String) -> AppError {
        .domain(DomainError(code: code, message: message))
    }
}
