package org.athletica.crm.usecases

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.settings.DbUserDisplaySettings
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.usecases.auth.SignUpError
import org.athletica.crm.usecases.auth.User
import org.athletica.crm.usecases.auth.signUp
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
            timezone = "UTC",
            currency = Currency.RUB,
        )

    @Test
    fun `signUp returns user on success`() =
        runTest {
            val userSettings = DbUserDisplaySettings()
            val result = TestPostgres.db.transaction { context(this, PasswordHasher(), userSettings) { signUp(request()) } }
            val user = assertIs<Either.Right<User>>(result).value
            assertEquals("user@example.com", user.username)
        }

    @Test
    fun `signUp returns UserAlreadyRegistered when login is taken`() =
        runTest {
            val userSettings = DbUserDisplaySettings()
            TestPostgres.db.transaction { context(this, PasswordHasher(), userSettings) { signUp(request()) } }
            // signUp поглощает R2DBC-исключение и возвращает Either.Left;
            // PostgreSQL переходит в error-state → commit падает. Захватываем результат до коммита.
            var result: Either<SignUpError, User>? = null
            runCatching {
                TestPostgres.db.transaction { context(this, PasswordHasher(), userSettings) { result = signUp(request()) } }
            }
            assertIs<Either.Left<SignUpError.UserAlreadyRegistered>>(result)
        }

    @Test
    fun `signUp allows different logins`() =
        runTest {
            val userSettings = DbUserDisplaySettings()
            assertIs<Either.Right<User>>(
                TestPostgres.db.transaction { context(this, PasswordHasher(), userSettings) { signUp(request(login = "user1@example.com")) } },
            )
            assertIs<Either.Right<User>>(
                TestPostgres.db.transaction { context(this, PasswordHasher(), userSettings) { signUp(request(login = "user2@example.com")) } },
            )
        }
}
