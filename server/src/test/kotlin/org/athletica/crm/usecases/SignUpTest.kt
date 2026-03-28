package org.athletica.crm.usecases

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.core.Lang
import org.athletica.crm.core.OrgId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.UserId
import org.athletica.crm.security.PasswordHasher
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

class SignUpTest {
    @Before
    fun setUp() = TestPostgres.truncate()

    private fun request(login: String = "user@example.com") =
        SignUpRequest(
            companyName = "Acme",
            userName = "John",
            login = login,
            password = "password123",
        )

    @Test
    fun `signUp returns user on success`() =
        runTest {
            context(TestPostgres.db, PasswordHasher(), RequestContext(lang = Lang.RU, userId = UserId(Uuid.generateV7()), orgId = OrgId(Uuid.generateV7()))) {
                val result = signUp(request())
                val user = assertIs<Either.Right<User>>(result).value
                assertEquals("user@example.com", user.username)
            }
        }

    @Test
    fun `signUp returns UserAlreadyRegistered when login is taken`() =
        runTest {
            context(TestPostgres.db, PasswordHasher(), RequestContext(lang = Lang.RU, userId = UserId(Uuid.generateV7()), orgId = OrgId(Uuid.generateV7()))) {
                signUp(request())
                val result = signUp(request())
                assertIs<Either.Left<SignUpError.UserAlreadyRegistered>>(result)
            }
        }

    @Test
    fun `signUp allows different logins`() =
        runTest {
            context(TestPostgres.db, PasswordHasher(), RequestContext(lang = Lang.RU, userId = UserId(Uuid.generateV7()), orgId = OrgId(Uuid.generateV7()))) {
                assertIs<Either.Right<User>>(signUp(request(login = "user1@example.com")))
                assertIs<Either.Right<User>>(signUp(request(login = "user2@example.com")))
            }
        }
}
