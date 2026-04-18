package org.athletica.crm.security

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import arrow.core.right
import io.r2dbc.spi.Row
import org.athletica.crm.core.OrgId
import org.athletica.crm.core.PasswordHash
import org.athletica.crm.core.UserId
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.toOrgId
import org.athletica.crm.core.toUserId
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.Transaction
import kotlin.uuid.toKotlinUuid

/**
 * Данные пользователя системы.
 * [id] — уникальный идентификатор, [orgId] — организация пользователя, [username] — имя для входа, [password] — хэш пароля.
 */
data class User(
    override val id: UserId,
    override val orgId: OrgId,
    override val username: String,
    val password: String,
) : AuthenticatedUser

/** Ошибка поиска пользователя: пользователь не найден по заданным критериям. */
data class UserNotFound(override val message: String) : DomainError {
    override val code: String = "USER_NOT_FOUND"
}

/**
 * Ищет пользователя по идентификатору [id].
 * Возвращает найденного пользователя, либо [UserNotFound].
 */
context(tr: Transaction, raise: Raise<UserNotFound>)
suspend fun userById(id: UserId): User =
    tr.sql(
        """
        SELECT u.*, e.org_id
        FROM users u
        JOIN employees e ON e.user_id = u.id
        WHERE u.id = :id AND e.is_active = true
        """.trimIndent(),
    )
        .bind("id", id)
        .firstOrNull { it.toUser() }
        ?: raise(UserNotFound("User with id='$id' not found"))

/** Делегирует вызов [userById] для данного UUID. Удобен при цепочке Either-операций. */
context(tr: Transaction, raise: Raise<UserNotFound>)
suspend fun UserId.mapUserById() = userById(this)

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
suspend fun findByCredentials(username: String, password: String): Either<UserNotFound, User> {
    val user =
        db
            .sql(
                """
                SELECT u.*, e.org_id
                FROM users u
                JOIN employees e ON e.user_id = u.id
                WHERE u.login = :username AND e.is_active = true
                """.trimIndent(),
            )
            .bind("username", username)
            .firstOrNull { it.toUser() }
    if (user == null) {
        return UserNotFound("User with given credentials not found").left()
    }
    val passwordIsValid = passwordHasher.verify(password, PasswordHash(user.password))
    return if (passwordIsValid) user.right() else UserNotFound("User with given credentials not found").left()
}

private fun Row.toUser() =
    User(
        id = get("id", java.util.UUID::class.java)!!.toKotlinUuid().toUserId(),
        orgId = get("org_id", java.util.UUID::class.java)!!.toKotlinUuid().toOrgId(),
        username = get("login", String::class.java)!!,
        password = get("password_hash", String::class.java)!!,
    )
