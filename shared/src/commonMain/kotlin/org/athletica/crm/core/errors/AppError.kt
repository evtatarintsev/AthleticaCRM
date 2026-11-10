package org.athletica.crm.core.errors

/**
 * Базовый тип ожидаемой ошибки приложения, поднимаемой через `Raise`.
 * Ошибка, не являющаяся [DomainError], означает сбой сервера: маршрут отвечает HTTP 500,
 * а [code] и [message] пишутся только в лог.
 */
interface AppError {
    /** Машиночитаемый код ошибки. */
    val code: String

    /** Описание ошибки. */
    val message: String
}

/**
 * Бизнес-ошибка: клиент нарушил правило предметной области или прислал некорректные данные.
 * Маршрут отвечает HTTP 400, [code] и [message] уходят клиенту, поэтому сообщение локализуется
 * и не содержит технических деталей.
 */
interface DomainError : AppError

/** Универсальная реализация [DomainError] для случаев, не требующих отдельного типа. */
data class CommonDomainError(
    override val code: String,
    override val message: String,
) : DomainError {
    override fun toString() = "$code: $message"
}
