package org.athletica.crm.security

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.OrgId
import org.athletica.crm.core.UserId
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UserServiceTest {
    private val hasher = PasswordHasher()

    @Before
    fun setUp() = TestPostgres.truncate()

    /**
     * Вставляет организацию, пользователя и запись сотрудника напрямую через [TestPostgres.db].
     * Возвращает UUID созданного пользователя.
     */
    private suspend fun insertUser(
        login: String,
        password: String,
    ): UserId {
        val orgId = OrgId.new()
        val userId = UserId.new()
        TestPostgres.db
            .sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
            .bind("id", orgId)
            .bind("name", login)
            .execute()
        TestPostgres.db
            .sql("INSERT INTO users (id, login, password_hash) VALUES (:id, :login, :hash)")
            .bind("id", userId)
            .bind("login", login)
            .bind("hash", hasher.hash(password).value)
            .execute()
        TestPostgres.db
            .sql("INSERT INTO employees (user_id, org_id, name, is_owner) VALUES (:userId, :orgId, :name, true)")
            .bind("userId", userId)
            .bind("orgId", orgId)
            .bind("name", login)
            .execute()
        return userId
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
                assertIs<Either.Left<UserNotFound>>(userById(UserId.new()))
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
