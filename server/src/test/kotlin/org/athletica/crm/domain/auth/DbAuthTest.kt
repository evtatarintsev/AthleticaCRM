package org.athletica.crm.domain.auth

import arrow.core.Either
import arrow.core.raise.context.either
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.Lang
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.domain.branch.Branch
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.security.UserNotFound
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DbAuthTest {
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
            val result =
                either {
                    TestPostgres.db.transaction { TestPostgres.auth.userById(id, BranchId.new()) }
                }
            val user = assertIs<Either.Right<AuthenticatedUser>>(result).value
            assertEquals("user1", user.username)
        }

    @Test
    fun `findById returns UserNotFound when user does not exist`() =
        runTest {
            val result =
                either {
                    TestPostgres.db.transaction {
                        TestPostgres.auth.userById(UserId.new(), BranchId.new())
                    }
                }
            assertIs<Either.Left<UserNotFound>>(result)
        }

    private suspend fun availableBranches(username: String, password: String) =
        either {
            TestPostgres.db.transaction { TestPostgres.auth.availableBranches(username, password, Lang.RU, clientIp = null) }
        }

    @Test
    fun `availableBranches accepts correct credentials`() =
        runTest {
            insertUser("user2", "secret123")
            assertIs<Either.Right<List<Branch>>>(availableBranches("user2", "secret123"))
        }

    @Test
    fun `availableBranches returns UserNotFound for wrong password`() =
        runTest {
            insertUser("user3", "correct_password")
            assertIs<Either.Left<UserNotFound>>(availableBranches("user3", "wrong_password"))
        }

    @Test
    fun `availableBranches returns UserNotFound for non-existent user`() =
        runTest {
            assertIs<Either.Left<UserNotFound>>(availableBranches("ghost_user", "any_password"))
        }
}
