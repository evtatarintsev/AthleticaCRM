package org.athletica.crm.admin

import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.storage.Database

/**
 * Пароли пользователей для административных операций — напрямую в БД, без RequestContext:
 * пользователь не принадлежит одной организации, а у CLI нет действующего сотрудника.
 * [database] — подключение к БД, [passwordHasher] — хеширование нового пароля.
 */
class UserPasswords(
    private val database: Database,
    private val passwordHasher: PasswordHasher,
) {
    /** Есть ли пользователь с логином [login] (точное совпадение, как при входе). */
    suspend fun exists(login: String): Boolean =
        database.transaction {
            orThrow {
                sql("SELECT 1 FROM users WHERE login = :login")
                    .bind("login", login)
                    .firstOrNull { true } ?: false
            }
        }

    /**
     * Заменяет пароль пользователя с логином [login] на [password] без проверки старого.
     * Возвращает `false`, если такого пользователя нет.
     */
    suspend fun reset(login: String, password: String): Boolean =
        database.transaction {
            orThrow {
                sql("UPDATE users SET password_hash = :hash WHERE login = :login")
                    .bind("hash", passwordHasher.hash(password).value)
                    .bind("login", login)
                    .execute() > 0
            }
        }
}
