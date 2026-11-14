package org.athletica.crm.storage

import io.r2dbc.postgresql.api.PostgresqlException
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import org.athletica.crm.core.errors.AppError

/**
 * Ошибка БД, поднимаемая [QueryBuilder] через `Raise`. Это не [org.athletica.crm.core.errors.DomainError]:
 * если репозиторий не перевёл её в доменную ошибку, маршрут ответит HTTP 500.
 */
sealed interface DbError : AppError

/**
 * Нарушение ограничения целостности (уникальность, внешний ключ, CHECK, NOT NULL).
 * [constraint] — имя нарушенного ограничения; `null`, если PostgreSQL его не сообщил (например, NOT NULL).
 * [message] — текст ошибки драйвера, только для лога.
 */
data class ConstraintViolation(
    val constraint: String?,
    override val message: String,
) : DbError {
    override val code = "CONSTRAINT_VIOLATION"
}

/** Ошибка [ConstraintViolation], соответствующая исключению драйвера. */
internal fun R2dbcDataIntegrityViolationException.toConstraintViolation(): ConstraintViolation =
    ConstraintViolation(
        constraint = (this as? PostgresqlException)?.errorDetails?.constraintName?.orElse(null),
        message = message.orEmpty(),
    )
