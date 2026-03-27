package org.athletica.crm.security

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class UserServiceTest {
    private val hasher = PasswordHasher()
    private val userService = UserService(db = TestPostgres.db, passwordHasher = hasher)

    @Before
    fun setUp() = TestPostgres.truncate()

    /** Вставляет пользователя через [TestPostgres.db], возвращает его UUID. */
    private suspend fun insertUser(
        login: String,
        password: String,
    ): Uuid {
        val id = Uuid.generateV7()
        TestPostgres.db
            .sql("INSERT INTO users (id, login, name, password_hash) VALUES (:id, :login, :name, :hash)")
            .bind("id", id)
            .bind("login", login)
            .bind("name", login)
            .bind("hash", hasher.hash(password).value)
            .execute()
        return id
    }

    @Test
    fun `findById returns user when exists`() =
        runTest {
            val id = insertUser("user1", "pass")
            val result = userService.findById(id)
            val user = assertIs<Either.Right<User>>(result).value
            assertEquals("user1", user.username)
        }

    @Test
    fun `findById returns UserNotFound when user does not exist`() =
        runTest {
            val result = userService.findById(Uuid.generateV7())
            assertIs<Either.Left<UserNotFound>>(result)
        }

    @Test
    fun `findByCredentials returns user for correct credentials`() =
        runTest {
            insertUser("user2", "secret123")
            val result = userService.findByCredentials("user2", "secret123")
            val user = assertIs<Either.Right<User>>(result).value
            assertEquals("user2", user.username)
        }

    @Test
    fun `findByCredentials returns UserNotFound for wrong password`() =
        runTest {
            insertUser("user3", "correct_password")
            val result = userService.findByCredentials("user3", "wrong_password")
            assertIs<Either.Left<UserNotFound>>(result)
        }

    @Test
    fun `findByCredentials returns UserNotFound for non-existent user`() =
        runTest {
            val result = userService.findByCredentials("ghost_user", "any_password")
            assertIs<Either.Left<UserNotFound>>(result)
        }
}
