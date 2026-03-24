package org.athletica.crm.security

import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
            val user = userService.findById(id)
            assertNotNull(user)
            assertEquals("user1", user.username)
        }

    @Test
    fun `findById returns null when user does not exist`() =
        runTest {
            val user = userService.findById(Uuid.generateV7())
            assertNull(user)
        }

    @Test
    fun `findByCredentials returns user for correct credentials`() =
        runTest {
            insertUser("user2", "secret123")
            val user = userService.findByCredentials("user2", "secret123")
            assertNotNull(user)
            assertEquals("user2", user.username)
        }

    @Test
    fun `findByCredentials returns null for wrong password`() =
        runTest {
            insertUser("user3", "correct_password")
            val user = userService.findByCredentials("user3", "wrong_password")
            assertNull(user)
        }

    @Test
    fun `findByCredentials returns null for non-existent user`() =
        runTest {
            val user = userService.findByCredentials("ghost_user", "any_password")
            assertNull(user)
        }
}
