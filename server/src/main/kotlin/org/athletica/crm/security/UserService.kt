package org.athletica.crm.security

data class User(val id: Int, val username: String, val password: String)

// TODO: заменить на запросы к БД
object UserService {
    private val users =
        listOf(
            User(id = 1, username = "admin", password = "admin"),
            User(id = 2, username = "user", password = "password"),
        )

    fun findByCredentials(
        username: String,
        password: String,
    ): User? = users.find { it.username == username && it.password == password }

    fun findById(id: Int): User? = users.find { it.id == id }
}
