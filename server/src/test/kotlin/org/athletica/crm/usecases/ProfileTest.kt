package org.athletica.crm.usecases

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestAuditLog
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.Lang
import org.athletica.crm.core.OrgId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.security.User
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProfileTest {
    private val hasher = PasswordHasher()

    @Before
    fun setUp() = TestPostgres.truncate()

    private suspend fun insertUser(login: String): Pair<UserId, OrgId> {
        val orgId = OrgId.new()
        val userId = UserId.new()
        TestPostgres.db
            .sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
            .bind("id", orgId)
            .bind("name", login)
            .execute()
        TestPostgres.db
            .sql("INSERT INTO users (id, login, name, password_hash) VALUES (:id, :login, :name, :hash)")
            .bind("id", userId)
            .bind("login", login)
            .bind("name", login)
            .bind("hash", hasher.hash("password").value)
            .execute()
        TestPostgres.db
            .sql("INSERT INTO employees (user_id, org_id, is_owner) VALUES (:userId, :orgId, true)")
            .bind("userId", userId)
            .bind("orgId", orgId)
            .execute()
        return userId to orgId
    }

    private fun requestContext(userId: UserId, orgId: OrgId, username: String = "") =
        RequestContext(
            lang = Lang.EN,
            userId = userId,
            orgId = orgId,
            username = username,
            clientIp = "127.0.0.1",
        )

    @Test
    fun `profile returns user for authenticated user`() =
        runTest {
            val (userId, orgId) = insertUser("user@example.com")
            context(TestPostgres.db, requestContext(userId, orgId), TestAuditLog()) {
                val result = profile()
                val user = assertIs<Either.Right<User>>(result).value
                assertEquals("user@example.com", user.username)
                assertEquals(userId, user.id)
                assertEquals(orgId, user.orgId)
            }
        }

    @Test
    fun `profile returns error when user does not exist`() =
        runTest {
            val ctx = requestContext(UserId.new(), OrgId.new())
            context(TestPostgres.db, ctx) {
                assertIs<Either.Left<DomainError>>(profile())
            }
        }
}
