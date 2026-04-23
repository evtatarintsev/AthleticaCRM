package org.athletica.crm.usecases

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.Lang
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.usecases.auth.UserProfile
import org.athletica.crm.usecases.auth.profile
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

    private fun requestContext(
        userId: UserId,
        orgId: OrgId,
        employeeId: EmployeeId,
        username: String = "",
    ) = RequestContext(
        lang = Lang.EN,
        userId = userId,
        orgId = orgId,
        employeeId = employeeId,
        username = username,
        clientIp = "127.0.0.1",
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
}
