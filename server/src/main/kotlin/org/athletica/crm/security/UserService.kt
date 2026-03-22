package org.athletica.crm.security

import io.r2dbc.spi.Row
import org.athletica.crm.core.PasswordHash
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.db.Database
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/**
 * Данные пользователя системы.
 *
 * @param id уникальный идентификатор
 * @param username имя пользователя для входа
 * @param password хэш пароля пользователя
 */
data class User(
    override val id: Uuid,
    override val username: String,
    val password: String,
) : AuthenticatedUser

/**
 * Сервис для работы с пользователями.
 *
 * @param db обёртка над пулом R2DBC соединений
 */
class UserService(private val db: Database, private val passwordHasher: PasswordHasher) {
    /**
     * Ищет пользователя по идентификатору.
     *
     * @param id идентификатор пользователя
     * @return пользователь если найден, иначе null
     */
    suspend fun findById(id: Uuid): User? =
        db.sql("SELECT * FROM users WHERE id = :id")
            .bind("id", id.toJavaUuid())
            .firstOrNull { row, _ -> row.toUser() }

    /**
     * Ищет пользователя по имени и паролю.
     *
     * Сначала ищет пользователя по логину, затем проверяет пароль через [PasswordHasher.verify].
     * Сравнение в коде обязательно, так как Argon2id использует случайную соль —
     * два хеша одного пароля всегда разные и не могут сравниваться в SQL.
     *
     * @param username имя пользователя
     * @param password сырой пароль
     * @return пользователь если найден и пароль верен, иначе null
     */
    suspend fun findByCredentials(username: String, password: String): User? {
        val user = db.sql("SELECT * FROM users WHERE login = :username")
            .bind("username", username)
            .firstOrNull { row, _ -> row.toUser() }
            ?: return null
        return if (passwordHasher.verify(password, PasswordHash(user.password))) user else null
    }
}

private fun Row.toUser() = User(
    id = get("id", java.util.UUID::class.java)!!.toKotlinUuid(),
    username = get("login", String::class.java)!!,
    password = get("password_hash", String::class.java)!!,
)
