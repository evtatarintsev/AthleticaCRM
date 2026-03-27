package org.athletica.crm.security

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.r2dbc.spi.Row
import org.athletica.crm.core.PasswordHash
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.db.Database
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/**
 * Данные пользователя системы.
 * [id] — уникальный идентификатор, [username] — имя для входа, [password] — хэш пароля.
 */
data class User(
    override val id: Uuid,
    override val username: String,
    val password: String,
) : AuthenticatedUser

data class UserNotFound(
    override val message: String,
) : DomainError {
    override val code: String = "USER_NOT_FOUND"
}

/**
 * Ищет пользователя по идентификатору [id].
 * Возвращает найденного пользователя, либо [UserNotFound].
 */
context(db: Database)
suspend fun userById(id: Uuid): Either<UserNotFound, User> =
    db
        .sql("SELECT * FROM users WHERE id = :id")
        .bind("id", id.toJavaUuid())
        .firstOrNull { row, _ -> row.toUser() }
        ?.right() ?: UserNotFound("User with id='$id' not found").left()

/**
 * Ищет пользователя по идентификатору [id].
 * Возвращает найденного пользователя, либо [UserNotFound].
 */
context(db: Database)
suspend fun Uuid.mapUserById() = userById(this)

/**
 * Ищет пользователя по имени и паролю.
 *
 * Сначала ищет пользователя по логину, затем проверяет пароль через [PasswordHasher.verify].
 * Сравнение в коде обязательно, так как Argon2id использует случайную соль —
 * два хеша одного пароля всегда разные и не могут сравниваться в SQL.
 *
 * Возвращает пользователя если [username] найден и [password] верен, либо [UserNotFound].
 */
context(db: Database, passwordHasher: PasswordHasher)
suspend fun findByCredentials(
    username: String,
    password: String,
): Either<UserNotFound, User> {
    val user =
        db
            .sql("SELECT * FROM users WHERE login = :username")
            .bind("username", username)
            .firstOrNull { row, _ -> row.toUser() }
    if (user == null) {
        return UserNotFound("User with given credentials not found").left()
    }
    val passwordIsValid = passwordHasher.verify(password, PasswordHash(user.password))
    return if (passwordIsValid) user.right() else UserNotFound("User with given credentials not found").left()
}

private fun Row.toUser() =
    User(
        id = get("id", java.util.UUID::class.java)!!.toKotlinUuid(),
        username = get("login", String::class.java)!!,
        password = get("password_hash", String::class.java)!!,
    )
