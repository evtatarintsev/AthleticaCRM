package org.athletica.crm.security

/**
 * Данные пользователя системы.
 *
 * @param id уникальный идентификатор
 * @param username имя пользователя для входа
 * @param password пароль пользователя
 */
data class User(
    val id: Int,
    val username: String,
    val password: String,
)

/**
 * Сервис для работы с пользователями.
 *
 * TODO: заменить заглушку на запросы к БД
 */
object UserService {
    private val users =
        listOf(
            User(id = 1, username = "admin", password = "admin"),
            User(id = 2, username = "user", password = "password"),
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
    fun findById(id: Int): User? = users.find { it.id == id }
}
