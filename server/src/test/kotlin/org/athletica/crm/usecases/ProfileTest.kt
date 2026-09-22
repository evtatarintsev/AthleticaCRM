package org.athletica.crm.usecases

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.UpdateMeRequest
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.usecases.auth.UserProfile
import org.athletica.crm.usecases.auth.profile
import org.athletica.crm.usecases.auth.updateMe
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProfileTest {
    private val hasher = PasswordHasher()

    @Before
    fun setUp() = TestPostgres.truncate()

    private suspend fun insertUser(login: String): Triple<UserId, OrgId, EmployeeId> {
        val orgId = OrgId.new()
        val userId = UserId.new()
        val employeeId = EmployeeId.new()
        TestPostgres.db
            .sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
            .bind("id", orgId)
            .bind("name", login)
            .execute()
        TestPostgres.db
            .sql("INSERT INTO branches (id, org_id, name) VALUES (:id, :orgId, 'Main')")
            .bind("id", branchId)
            .bind("orgId", orgId)
            .execute()
        TestPostgres.db
            .sql("INSERT INTO users (id, login, password_hash) VALUES (:id, :login, :hash)")
            .bind("id", userId)
            .bind("login", login)
            .bind("hash", hasher.hash("password").value)
            .execute()
        TestPostgres.db
            .sql("INSERT INTO employees (id, user_id, org_id, name, is_owner) VALUES (:id, :userId, :orgId, :name, true)")
            .bind("id", employeeId)
            .bind("userId", userId)
            .bind("orgId", orgId)
            .bind("name", login)
            .execute()
        return Triple(userId, orgId, employeeId)
    }

    private val branchId = BranchId.new()

    private fun requestContext(
        userId: UserId,
        orgId: OrgId,
        employeeId: EmployeeId,
        username: String = "",
    ) = EmployeeRequestContext(
        lang = Lang.EN,
        userId = userId,
        orgId = orgId,
        branchId = branchId,
        employeeId = employeeId,
        username = username,
        clientIp = "127.0.0.1",
        currency = Currency.RUB,
        permission = EmployeePermission(),
    )

    @Test
    fun `profile returns user for authenticated user`() =
        runTest {
            val (userId, orgId, employeeId) = insertUser("user@example.com")
            context(TestPostgres.db, requestContext(userId, orgId, employeeId), ChangePasswordTest()) {
                val result = profile()
                val user = assertIs<Either.Right<UserProfile>>(result).value
                assertEquals("user@example.com", user.username)
                assertEquals("user@example.com", user.name)
                assertEquals(userId, user.id)
                assertEquals(orgId, user.orgId)
                assertEquals(employeeId, user.employeeId)
                assertEquals(null, user.avatarId)
            }
        }

    @Test
    fun `profile returns error when user does not exist`() =
        runTest {
            val ctx = requestContext(UserId.new(), OrgId.new(), EmployeeId.new())
            context(TestPostgres.db, ctx) {
                assertIs<Either.Left<DomainError>>(profile())
            }
        }

    @Test
    fun `updateMe сохраняет аватар`() =
        runTest {
            val (userId, orgId, employeeId) = insertUser("avatar@example.com")
            val uploadId = UploadId.new()
            TestPostgres.db
                .sql(
                    """
                    INSERT INTO uploads (id, org_id, uploaded_by, object_key, original_name, content_type, size_bytes)
                    VALUES (:id, :orgId, :userId, 'k', 'n', 'image/png', 1)
                    """.trimIndent(),
                )
                .bind("id", uploadId)
                .bind("orgId", orgId)
                .bind("userId", userId)
                .execute()

            context(TestPostgres.db, requestContext(userId, orgId, employeeId)) {
                val updated = assertIs<Either.Right<UserProfile>>(updateMe(UpdateMeRequest("New Name", uploadId))).value
                assertEquals(uploadId, updated.avatarId)

                val reloaded = assertIs<Either.Right<UserProfile>>(profile()).value
                assertEquals(uploadId, reloaded.avatarId)
                assertEquals("New Name", reloaded.name)
            }
        }
}
