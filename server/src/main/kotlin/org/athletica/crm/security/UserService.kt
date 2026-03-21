package org.athletica.crm.security

import org.athletica.crm.core.auth.AuthenticatedUser
import kotlin.uuid.Uuid

/**
 * Данные пользователя системы.
 *
 * @param id уникальный идентификатор
 * @param username имя пользователя для входа
 * @param password пароль пользователя
 */
data class User(
    override val id: Uuid,
    override val username: String,
    val password: String,
) : AuthenticatedUser

/**
 * Сервис для работы с пользователями.
 *
 * TODO: заменить заглушку на запросы к БД
 */
object UserService {
    private val users =
        listOf(
            User(id = Uuid.generateV7(), username = "admin", password = "admin"),
            User(id = Uuid.generateV7(), username = "user", password = "password"),
        )

    /**
     * Ищет пользователя по имени и паролю.
     *
     * @param username имя пользователя
     * @param password пароль пользователя
     * @return пользователь если найден, иначе null
     */
    fun findByCredentials(
        username: String,
        password: String,
    ): User? = users.find { it.username == username && it.password == password }

    /**
     * Ищет пользователя по идентификатору.
     *
     * @param id идентификатор пользователя
     * @return пользователь если найден, иначе null
     */
    fun findById(id: Uuid): User? = users.find { it.id == id }
}
