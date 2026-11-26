package org.athletica.crm.usecases.clientapp

import arrow.core.Either
import arrow.core.raise.context.either
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.clientapp.ClientAppFixture
import org.athletica.crm.core.Lang
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.domain.clientapp.auth.CodeIssue
import org.athletica.crm.domain.clientapp.auth.DbClientAppAccounts
import org.athletica.crm.domain.clientapp.auth.DbClientAppCodes
import org.athletica.crm.domain.clientapp.auth.DbClientAppSessions
import org.athletica.crm.storage.asInt
import org.athletica.crm.storage.asString
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/** Тесты входа в кабинет по коду ([ClientAppSignIn]): одинаковый ответ, условия и попытки. */
class ClientAppSignInTest {
    private val phone = "+7 903 123-45-67"

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    /** Вход с текущей версией условий [terms]. */
    private fun signIn(terms: String = "v2") = ClientAppSignIn(TestPostgres.db, DbClientAppCodes(), DbClientAppAccounts(), DbClientAppSessions(), terms, 60.days)

    /** Выпускает код на [phone] номер [rawPhone]. */
    private suspend fun issued(rawPhone: String = phone, sign: ClientAppSignIn = signIn()): CodeIssue.Issued = assertIs<CodeIssue.Issued>(orThrow { sign.requestCode(rawPhone, Lang.RU, Clock.System.now()) })

    /** Входит с кодом [code] и принятой версией [accepted]. */
    private suspend fun login(code: String, accepted: String?, sign: ClientAppSignIn = signIn()): Either<AppError, SignedIn> = either { sign.login(phone, code, accepted, "Safari, iOS", Lang.RU, Clock.System.now()) }

    /** Попытки, потраченные на последний код. */
    private suspend fun attempts(): Int = orThrow { checkNotNull(TestPostgres.db.sql("SELECT attempts FROM client_app_codes ORDER BY created_at DESC LIMIT 1").firstOrNull { it.asInt("attempts") }) }

    @Test
    fun `ответ для постороннего и приглашённого номера одинаков`() =
        runTest {
            orThrow { ClientAppFixture.client(ClientAppFixture.org("Динамо"), phone) }

            val invited = issued()
            val stranger = issued("+7 999 000-00-00")

            assertEquals(invited.retryAfter, stranger.retryAfter)
            assertEquals(60.seconds, invited.retryAfter)
        }

    @Test
    fun `первый вход требует условий и не тратит попытку`() =
        runTest {
            val code = issued().code.value

            assertEquals("CLIENT_APP_TERMS_REQUIRED", assertIs<Either.Left<AppError>>(login(code, accepted = null)).value.code)
            assertEquals(0, attempts())

            assertIs<Either.Right<SignedIn>>(login(code, accepted = "v2"))
            val version = orThrow { TestPostgres.db.sql("SELECT terms_version FROM client_app_accounts").firstOrNull { it.asString("terms_version") } }
            assertEquals("v2", version)
        }

    @Test
    fun `новая версия условий требует принятия, хотя прежняя принята`() =
        runTest {
            assertIs<Either.Right<SignedIn>>(login(issued(sign = signIn("v1")).code.value, accepted = "v1", sign = signIn("v1")))
            orThrow { TestPostgres.db.sql("UPDATE client_app_codes SET created_at = created_at - interval '2 minutes'").execute() }

            val code = issued().code.value
            assertEquals("CLIENT_APP_TERMS_REQUIRED", assertIs<Either.Left<AppError>>(login(code, accepted = "v1")).value.code)
            assertIs<Either.Right<SignedIn>>(login(code, accepted = "v2"))
        }

    @Test
    fun `принятые условия повторно не спрашиваются, неверный код тратит попытку`() =
        runTest {
            assertIs<Either.Right<SignedIn>>(login(issued().code.value, accepted = "v2"))
            orThrow { TestPostgres.db.sql("UPDATE client_app_codes SET created_at = created_at - interval '2 minutes'").execute() }
            val code = issued().code.value

            val wrong = if (code == "000000") "111111" else "000000"
            assertEquals("CLIENT_APP_CODE_WRONG", assertIs<Either.Left<AppError>>(login(wrong, accepted = null)).value.code)
            assertEquals(1, attempts())
            assertIs<Either.Right<SignedIn>>(login(code, accepted = null))
        }

    @Test
    fun `тот же номер в другом формате — тот же аккаунт`() =
        runTest {
            val first = assertIs<Either.Right<SignedIn>>(login(issued().code.value, accepted = "v2")).value
            orThrow { TestPostgres.db.sql("UPDATE client_app_codes SET created_at = created_at - interval '2 minutes'").execute() }
            val code = issued("89031234567").code.value

            val second = assertIs<Either.Right<SignedIn>>(login(code, accepted = null)).value
            assertEquals(first.accountId, second.accountId)
        }
}
