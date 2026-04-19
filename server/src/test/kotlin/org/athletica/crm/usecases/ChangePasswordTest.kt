package org.athletica.crm.usecases

import arrow.core.Either
import arrow.core.raise.context.either
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.ChangePasswordRequest
import org.athletica.crm.core.Lang
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.audit.PostgresAuditLog
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.security.findByCredentials
import org.athletica.crm.usecases.auth.changePassword
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ChangePasswordTest {
    private val hasher = PasswordHasher()

    @Before
    fun setUp() = TestPostgres.truncate()

    private suspend fun insertUser(
        login: String,
        password: String = "oldPass123",
    ): Pair<UserId, OrgId> {
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
        return userId to orgId
    }

    private fun ctx(
        userId: UserId,
        orgId: OrgId,
        username: String = "user",
    ) = RequestContext(
        lang = Lang.EN,
        userId = userId,
        orgId = orgId,
        username = username,
        clientIp = "127.0.0.1",
    )

    private suspend fun runChangePassword(
        userId: UserId,
        orgId: OrgId,
        request: ChangePasswordRequest,
        username: String = "user",
        audit: PostgresAuditLog = PostgresAuditLog(),
    ): Either<DomainError, Unit> =
        either {
            TestPostgres.db.transaction {
                context(ctx(userId, orgId, username), this, hasher, audit) {
                    changePassword(request)
                }
            }
        }

    // ── успешная смена ────────────────────────────────────────────────────────

    @Test
    fun `changePassword succeeds with correct old password`() =
        runTest {
            val (userId, orgId) = insertUser("user@example.com")
            val result = runChangePassword(userId, orgId, ChangePasswordRequest("oldPass123", "newPass456"))
            assertIs<Either.Right<Unit>>(result)
        }

    @Test
    fun `new password works for login after change`() =
        runTest {
            val login = "user@example.com"
            val (userId, orgId) = insertUser(login, "oldPass123")
            runChangePassword(userId, orgId, ChangePasswordRequest("oldPass123", "newPass456"))
            context(TestPostgres.db, hasher) {
                assertIs<Either.Right<*>>(findByCredentials(login, "newPass456"))
            }
        }

    @Test
    fun `old password is rejected after change`() =
        runTest {
            val login = "user@example.com"
            val (userId, orgId) = insertUser(login, "oldPass123")
            runChangePassword(userId, orgId, ChangePasswordRequest("oldPass123", "newPass456"))
            context(TestPostgres.db, hasher) {
                assertIs<Either.Left<*>>(findByCredentials(login, "oldPass123"))
            }
        }

    // ── ошибки ────────────────────────────────────────────────────────────────

    @Test
    fun `changePassword returns WRONG_PASSWORD for incorrect old password`() =
        runTest {
            val (userId, orgId) = insertUser("user@example.com", "oldPass123")
            val result = runChangePassword(userId, orgId, ChangePasswordRequest("wrongPass", "newPass456"))
            val error = assertIs<Either.Left<CommonDomainError>>(result).value
            assertEquals("WRONG_PASSWORD", error.code)
        }

    @Test
    fun `changePassword returns error when user does not exist`() =
        runTest {
            val result = runChangePassword(UserId.new(), OrgId.new(), ChangePasswordRequest("oldPass123", "newPass456"))
            assertIs<Either.Left<DomainError>>(result)
        }

    @Test
    fun `original password unchanged when old password is wrong`() =
        runTest {
            val login = "user@example.com"
            val (userId, orgId) = insertUser(login, "oldPass123")
            runChangePassword(userId, orgId, ChangePasswordRequest("wrongPass", "newPass456"))
            context(TestPostgres.db, hasher) {
                assertIs<Either.Right<*>>(findByCredentials(login, "oldPass123"))
            }
        }
}
