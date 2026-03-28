package org.athletica.crm.usecases

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.security.PasswordHasher
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
            context(TestPostgres.db, PasswordHasher()) {
                val result = signUp(request())
                val user = assertIs<Either.Right<User>>(result).value
                assertEquals("user@example.com", user.username)
            }
        }

    @Test
    fun `signUp returns UserAlreadyRegistered when login is taken`() =
        runTest {
            context(TestPostgres.db, PasswordHasher()) {
                signUp(request())
                val result = signUp(request())
                assertIs<Either.Left<SignUpError.UserAlreadyRegistered>>(result)
            }
        }

    @Test
    fun `signUp allows different logins`() =
        runTest {
            context(TestPostgres.db, PasswordHasher()) {
                assertIs<Either.Right<User>>(signUp(request(login = "user1@example.com")))
                assertIs<Either.Right<User>>(signUp(request(login = "user2@example.com")))
            }
        }
}
