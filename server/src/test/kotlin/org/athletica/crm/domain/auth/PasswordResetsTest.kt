package org.athletica.crm.domain.auth

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.athletica.crm.Di
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.core.Lang
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.audit.AuditEvent
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.PostgresAuditLog
import org.athletica.crm.domain.mail.DbOrgEmails
import org.athletica.crm.domain.notifications.DbNotifications
import org.athletica.crm.openTestSession
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asLong
import org.athletica.crm.storage.asString
import org.athletica.crm.testDi
import org.athletica.crm.testName
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class PasswordResetsTest {
    private val db = TestPostgres.db
    private val config = EmailVerificationConfig("https://app.example.com")
    private val login = "owner@example.com"

    @Before
    fun setUp() = TestPostgres.truncate()

    private suspend fun signup(di: Di = testDi()): AuthenticatedUser =
        orThrow {
            db.transaction { di.auth.signup(SignUpRequest(testName("Club"), testName("Owner"), login, "old-password", "UTC", Currency.RUB), Lang.EN, null) }
        }

    private fun service(
        audit: AuditLog = PostgresAuditLog(),
        clock: Clock = Clock.System,
    ): PasswordResets {
        val emails = DbOrgEmails()
        val sessions = DbAuthSessions()
        return PasswordResets(emails, sessions, EmailVerifications(emails, DbNotifications(), audit, config), audit, PasswordHasher(), config, clock)
    }

    private suspend fun request(
        email: String = login,
        resets: PasswordResets = service(),
    ) = orThrow { db.transaction { resets.request(email, Lang.EN) } }

    private suspend fun reset(
        token: String,
        password: String = "new-password",
        resets: PasswordResets = service(),
    ) = orThrow { db.transaction { resets.reset(token, password, Lang.EN, "192.0.2.9") } }

    private suspend fun resetEmails(): List<String> = orThrow { db.sql("SELECT text_body FROM org_emails WHERE text_body LIKE '%reset-password#token=%' ORDER BY created_at").list { it.asString("text_body") } }

    private suspend fun token(): String = resetEmails().last().substringAfter("#token=").substringBefore('\n').trim()

    private suspend fun count(sql: String): Long = orThrow { db.sql("SELECT count(*) AS n FROM $sql").firstOrNull { it.asLong("n") }!! }

    private suspend fun hash(): String = orThrow { db.sql("SELECT password_hash FROM users").firstOrNull { it.asString("password_hash") }!! }

    private suspend fun allowNextEmail() = orThrow { db.sql("UPDATE password_resets SET last_sent_at = last_sent_at - interval '61 seconds'").execute() }

    private suspend fun canLogin(
        user: AuthenticatedUser,
        password: String,
    ): Boolean = runCatching { orThrow { db.transaction { testDi().auth.login(user.username, password, user.branchId, Lang.EN, null) } } }.isSuccess

    @Test
    fun `request queues one localized email with a one hour link for an active employee`() =
        runTest {
            signup()
            request()
            val emails = resetEmails()
            assertEquals(1, emails.size)
            assertContains(emails.single(), "https://app.example.com/reset-password#token=")
            assertFalse(emails.single().contains("old-password"))
            assertEquals(43, token().length)
            orThrow {
                db.transaction {
                    val row = sql("SELECT token_hash, expires_at, last_sent_at FROM password_resets").firstOrNull { it.asString("token_hash") to (it.asInstant("expires_at") - it.asInstant("last_sent_at")) }!!
                    assertNotEquals(token(), row.first)
                    assertEquals(1.hours, row.second)
                }
            }
        }

    @Test
    fun `unknown and blocked addresses get no email`() =
        runTest {
            signup()
            request("nobody@example.com")
            orThrow { db.sql("UPDATE employees SET blocked_at = now()").execute() }
            request()
            assertEquals(0, resetEmails().size)
            assertEquals(0L, count("password_resets"))
        }

    @Test
    fun `requests within a minute are ignored and a later one replaces the previous link`() =
        runTest {
            signup()
            request()
            val first = token()
            val firstHash = orThrow { db.sql("SELECT token_hash FROM password_resets").firstOrNull { it.asString("token_hash") } }
            request()
            assertEquals(1, resetEmails().size)
            assertEquals(firstHash, orThrow { db.sql("SELECT token_hash FROM password_resets").firstOrNull { it.asString("token_hash") } })
            allowNextEmail()
            request()
            assertEquals(2, resetEmails().size)
            assertFails { reset(first) }
            reset(token())
        }

    @Test
    fun `parallel requests queue a single email`() =
        runTest {
            signup()
            val resets = service()
            coroutineScope { List(3) { async(Dispatchers.Default) { request(resets = resets) } }.awaitAll() }
            assertEquals(1, resetEmails().size)
        }

    @Test
    fun `reset changes the password ends every session confirms email and writes audit`() =
        runTest {
            val user = signup()
            openTestSession(user)
            openTestSession(user)
            request()
            reset(token())
            assertEquals(0L, count("auth_sessions"))
            assertFalse(canLogin(user, "old-password"))
            assertEquals(true, canLogin(user, "new-password"))
            assertEquals(1L, count("audit_logs WHERE action_type = 'auth_password_reset' AND ip_address = '192.0.2.9'"))
            assertEquals(1L, count("user_email_verifications WHERE verified_at IS NOT NULL"))
            assertEquals(1L, count("notifications"))
        }

    @Test
    fun `used unknown expired and blocked links are rejected without changes`() =
        runTest {
            val user = signup()
            request()
            val token = token()
            val before = hash()
            val expiresAt = orThrow { db.sql("SELECT expires_at FROM password_resets").firstOrNull { it.asInstant("expires_at") }!! }
            val atExpiry =
                object : Clock {
                    override fun now(): Instant = expiresAt
                }
            listOf("unknown", "A".repeat(43)).forEach { assertFails { reset(it) } }
            assertFails { reset(token, resets = service(clock = atExpiry)) }
            orThrow { db.sql("UPDATE employees SET blocked_at = now()").execute() }
            openTestSession(user)
            assertFails { reset(token) }
            assertEquals(before, hash())
            assertEquals(1L, count("auth_sessions"))
            orThrow { db.sql("UPDATE employees SET blocked_at = NULL").execute() }
            reset(token)
            val error = runCatching { reset(token, "another") }.exceptionOrNull()
            assertContains(error?.message.orEmpty(), "invalid")
            assertFalse(error?.message.orEmpty().contains(token))
            assertEquals(true, canLogin(user, "new-password"))
        }

    @Test
    fun `parallel resets with one link change the password once`() =
        runTest {
            signup()
            request()
            val token = token()
            val resets = service()
            val results = coroutineScope { List(2) { async(Dispatchers.Default) { runCatching { reset(token, resets = resets) }.isSuccess } }.awaitAll() }
            assertEquals(listOf(false, true), results.sorted())
            assertEquals(1L, count("audit_logs WHERE action_type = 'auth_password_reset'"))
        }

    @Test
    fun `empty password and audit failure keep the link usable`() =
        runTest {
            val user = signup()
            openTestSession(user)
            request()
            val token = token()
            val before = hash()
            val badAudit =
                object : AuditLog {
                    context(tr: Transaction, raise: Raise<AppError>)
                    override suspend fun log(event: AuditEvent) {
                        raise(CommonDomainError("AUDIT_FAILED", "Audit failed"))
                    }
                }
            assertFails { reset(token, "") }
            assertFails { reset(token, resets = service(audit = badAudit)) }
            assertEquals(before, hash())
            assertEquals(1L, count("auth_sessions"))
            assertEquals(1L, count("password_resets WHERE used_at IS NULL"))
            reset(token)
            assertEquals(true, canLogin(user, "new-password"))
        }
}
