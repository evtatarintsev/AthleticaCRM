package org.athletica.crm.core.errors

import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.either

/**
 * Исключение-обёртка для [error], которую некому вернуть или нечем объяснить клиенту:
 * в маршруте она уходит в `StatusPages` и превращается в HTTP 500 с записью в лог.
 */
class UnhandledAppError(val error: AppError) : RuntimeException("${error.code}: ${error.message}")

/**
 * Выполняет [block] и превращает поднятую в нём [AppError] в [UnhandledAppError].
 * Для кода вне HTTP-запроса — фоновых задач, CLI, тестовых фикстур, — где ожидаемой ошибки быть не должно.
 */
inline fun <T> orThrow(block: Raise<AppError>.() -> T): T = either(block).getOrElse { throw UnhandledAppError(it) }
