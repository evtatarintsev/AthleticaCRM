package org.athletica.crm.usecases

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.SignUpRequest
import org.athletica.crm.security.PasswordHasher
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SignUpTest {
    private val signUp = SignUp(db = TestPostgres.db, passwordHasher = PasswordHasher())

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
            val result = signUp.signUp(request())
            val user = assertIs<Either.Right<User>>(result).value
            assertEquals("user@example.com", user.username)
        }

    @Test
    fun `signUp returns UserAlreadyRegistered when login is taken`() =
        runTest {
            signUp.signUp(request())
            val result = signUp.signUp(request(login = "user@example.com"))
            assertIs<Either.Left<SignUpError.UserAlreadyRegistered>>(result)
        }

    @Test
    fun `signUp allows different logins`() =
        runTest {
            assertIs<Either.Right<User>>(signUp.signUp(request(login = "user1@example.com")))
            assertIs<Either.Right<User>>(signUp.signUp(request(login = "user2@example.com")))
        }
}
