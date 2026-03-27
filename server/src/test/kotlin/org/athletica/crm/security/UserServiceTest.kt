package org.athletica.crm.security

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

class UserServiceTest {
    private val hasher = PasswordHasher()

    @Before
    fun setUp() = TestPostgres.truncate()

    /** Вставляет пользователя напрямую через [TestPostgres.db], возвращает его UUID. */
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
            context(TestPostgres.db) {
                val result = userById(id)
                val user = assertIs<Either.Right<User>>(result).value
                assertEquals("user1", user.username)
            }
        }

    @Test
    fun `findById returns UserNotFound when user does not exist`() =
        runTest {
            context(TestPostgres.db) {
                assertIs<Either.Left<UserNotFound>>(userById(Uuid.generateV7()))
            }
        }

    @Test
    fun `findByCredentials returns user for correct credentials`() =
        runTest {
            insertUser("user2", "secret123")
            context(TestPostgres.db, hasher) {
                val result = findByCredentials("user2", "secret123")
                val user = assertIs<Either.Right<User>>(result).value
                assertEquals("user2", user.username)
            }
        }

    @Test
    fun `findByCredentials returns UserNotFound for wrong password`() =
        runTest {
            insertUser("user3", "correct_password")
            context(TestPostgres.db, hasher) {
                assertIs<Either.Left<UserNotFound>>(findByCredentials("user3", "wrong_password"))
            }
        }

    @Test
    fun `findByCredentials returns UserNotFound for non-existent user`() =
        runTest {
            context(TestPostgres.db, hasher) {
                assertIs<Either.Left<UserNotFound>>(findByCredentials("ghost_user", "any_password"))
            }
        }
}
