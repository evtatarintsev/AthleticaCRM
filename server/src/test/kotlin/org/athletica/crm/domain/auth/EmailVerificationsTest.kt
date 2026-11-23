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
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.Lang
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.audit.AuditEvent
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.PostgresAuditLog
import org.athletica.crm.domain.mail.DbOrgEmails
import org.athletica.crm.domain.mail.OrgEmails
import org.athletica.crm.domain.notifications.DbNotifications
import org.athletica.crm.domain.notifications.Notification
import org.athletica.crm.domain.notifications.NotificationContent
import org.athletica.crm.domain.notifications.Notifications
import org.athletica.crm.employeeContext
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asInstantOrNull
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
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class EmailVerificationsTest {
    private val db = TestPostgres.db
    private val config = EmailVerificationConfig("https://app.example.com/web/")

    @Before
    fun setUp() = TestPostgres.truncate()

    private fun request(login: String = "owner@example.com") = SignUp(testName("Club"), testName("Owner"), login, "password123", "UTC", Currency.RUB)

    private suspend fun signup(di: Di = testDi(), login: String = "owner@example.com"): AuthenticatedUser =
        orThrow {
            db.transaction { di.auth.signup(request(login), Lang.EN, "127.0.0.1") }
        }

    private fun ctx(user: AuthenticatedUser) = employeeContext(user.orgId, user.branchId, user.employeeId, userId = user.id, username = user.username, lang = Lang.EN)

    private fun service(
        emails: OrgEmails = DbOrgEmails(),
        notifications: Notifications = DbNotifications(),
        audit: AuditLog = PostgresAuditLog(),
        clock: Clock = Clock.System,
    ) = EmailVerifications(emails, notifications, audit, config, clock)

    private suspend fun token(): String =
        orThrow {
            db.sql("SELECT text_body FROM org_emails ORDER BY created_at DESC LIMIT 1").firstOrNull { it.asString("text_body") }!!.substringAfter("#token=").substringBefore('\n').trim()
        }

    private suspend fun count(table: String): Long = orThrow { db.sql("SELECT count(*) AS n FROM $table").firstOrNull { it.asLong("n") }!! }

    private suspend fun allowResend() = orThrow { db.sql("UPDATE user_email_verifications SET last_sent_at = now() - interval '61 seconds'").execute() }

    private val failingEmails =
        object : OrgEmails by DbOrgEmails() {
            context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
            override suspend fun schedule(
                to: List<EmailAddress>,
                subject: String,
                textBody: String,
                htmlBody: String,
            ) {
                raise(CommonDomainError("MAIL_FAILED", "Mail failed"))
            }
        }

    @Test
    fun `signup queues a welcome message and stores a hash with a 24 hour lifetime`() =
        runTest {
            val di = testDi()
            val user = signup(di)
            val token = token()
            assertEquals(43, token.length)
            assertEquals(1L, count("org_emails"))
            orThrow {
                db.transaction {
                    val row = sql("SELECT * FROM user_email_verifications").firstOrNull { Triple(it.asString("token_hash"), it.asInstant("expires_at") - it.asInstant("last_sent_at"), it.asInstantOrNull("verified_at")) }!!
                    assertEquals(64, row.first.length)
                    assertNotEquals(token, row.first)
                    assertEquals(24.hours, row.second)
                    assertNull(row.third)
                    assertTrue(di.emailVerifications.pending(user.id))
                    assertEquals(user.id, di.auth.login(user.username, "password123", user.branchId, Lang.EN, null).id)
                }
            }
            val body = orThrow { db.sql("SELECT text_body FROM org_emails").firstOrNull { it.asString("text_body") }!! }
            assertContains(body, "Welcome to AthleticaCRM")
            assertFalse(body.contains("password123"))
        }

    @Test
    fun `signup rolls back when mail cannot be queued and rejects invalid email`() =
        runTest {
            assertFails { signup(testDi().copy(orgEmails = failingEmails)) }
            assertEquals(0L, count("organizations"))
            assertEquals(0L, count("users"))
            assertEquals(0L, count("user_email_verifications"))
            for (email in listOf("invalid", "Name <owner@example.com>", "owner@example.com\r\nBcc: victim@example.com")) {
                assertFails { signup(login = email) }
                assertEquals(0L, count("users"))
            }
        }

    @Test
    fun `resend is rate limited then rotates the token and invalidates the old link`() =
        runTest {
            val user = signup()
            val old = token()
            val verification = service()
            orThrow {
                db.transaction {
                    context(ctx(user)) {
                        val limited = assertIs<ResendResult.RateLimited>(verification.resend())
                        assertTrue(limited.retryAfterSeconds in 1..60)
                    }
                }
            }
            assertEquals(1L, count("org_emails"))
            allowResend()
            orThrow { db.transaction { context(ctx(user)) { assertIs<ResendResult.Queued>(verification.resend()) } } }
            val fresh = token()
            assertNotEquals(old, fresh)
            assertEquals(2L, count("org_emails"))
            assertFails { orThrow { db.transaction { verification.confirm(old, Lang.EN, null) } } }
            orThrow { db.transaction { assertEquals(ConfirmResult.CONFIRMED, verification.confirm(fresh, Lang.EN, null)) } }
            orThrow { db.transaction { context(ctx(user)) { assertEquals(ResendResult.AlreadyConfirmed, verification.resend()) } } }
            assertEquals(2L, count("org_emails"))
        }

    @Test
    fun `parallel resend queues one message and failed queueing preserves the old link`() =
        runTest {
            val user = signup()
            allowResend()
            val old = token()
            assertFails { orThrow { db.transaction { context(ctx(user)) { service(emails = failingEmails).resend() } } } }
            assertEquals(1L, count("org_emails"))
            val verification = service()
            val statuses = coroutineScope { List(2) { async(Dispatchers.Default) { orThrow { db.transaction { context(ctx(user)) { verification.resend()::class } } } } }.awaitAll() }
            assertEquals(setOf(ResendResult.Queued::class, ResendResult.RateLimited::class), statuses.toSet())
            assertEquals(2L, count("org_emails"))
            assertNotEquals(old, token())
        }

    @Test
    fun `a failed resend does not revoke the original token`() =
        runTest {
            val user = signup()
            val original = token()
            allowResend()
            assertFails { orThrow { db.transaction { context(ctx(user)) { service(emails = failingEmails).resend() } } } }
            orThrow { db.transaction { assertEquals(ConfirmResult.CONFIRMED, service().confirm(original, Lang.EN, null)) } }
        }

    @Test
    fun `confirmation is single use under concurrency with one notification and audit`() =
        runTest {
            val user = signup()
            val token = token()
            val verification = service()
            val statuses = coroutineScope { List(2) { async(Dispatchers.Default) { orThrow { db.transaction { verification.confirm(token, Lang.EN, "192.0.2.1") } } } }.awaitAll() }
            assertEquals(setOf(ConfirmResult.CONFIRMED, ConfirmResult.ALREADY_CONFIRMED), statuses.toSet())
            assertEquals(1L, count("notifications"))
            orThrow {
                db.transaction {
                    val whenConfirmed = sql("SELECT verified_at FROM user_email_verifications").firstOrNull { it.asInstant("verified_at") }!!
                    assertEquals(ConfirmResult.ALREADY_CONFIRMED, verification.confirm(token, Lang.RU, null))
                    assertEquals(whenConfirmed, sql("SELECT verified_at FROM user_email_verifications").firstOrNull { it.asInstant("verified_at") })
                    assertFalse(verification.pending(user.id))
                    val audit = sql("SELECT data::text AS data, ip_address FROM audit_logs WHERE action_type = 'auth_verify_email'").list { it.asString("data") to it.asString("ip_address") }.single()
                    assertContains(audit.first, user.username)
                    assertFalse(audit.first.contains(token))
                    assertEquals("192.0.2.1", audit.second)
                    context(ctx(user)) {
                        val notification = DbNotifications().of().single()
                        assertContains(notification.body(Lang.EN), user.username)
                        assertEquals("Email confirmed", notification.title(Lang.EN))
                        assertEquals("Email подтверждён", notification.title(Lang.RU))
                    }
                }
            }
        }

    @Test
    fun `confirmation rejects unknown changed and exactly expired links without effects`() =
        runTest {
            signup()
            val token = token()
            val expiresAt = orThrow { db.sql("SELECT expires_at FROM user_email_verifications").firstOrNull { it.asInstant("expires_at") }!! }
            val exactExpiry =
                object : Clock {
                    override fun now() = expiresAt
                }
            for (invalid in listOf("unknown", "A".repeat(43), token)) {
                assertFails { orThrow { db.transaction { service(clock = exactExpiry).confirm(invalid, Lang.EN, null) } } }
            }
            orThrow { db.sql("UPDATE users SET login = 'changed@example.com'").execute() }
            assertFails { orThrow { db.transaction { service().confirm(token, Lang.EN, null) } } }
            assertEquals(0L, count("notifications"))
            assertNull(orThrow { db.sql("SELECT verified_at FROM user_email_verifications").firstOrNull { it.asInstantOrNull("verified_at")?.toString() ?: "pending" } }.takeUnless { it == "pending" })
        }

    @Test
    fun `confirmation rolls back date and notification on audit or notification failure`() =
        runTest {
            signup()
            val token = token()
            val badAudit =
                object : AuditLog {
                    context(tr: Transaction, raise: Raise<AppError>)
                    override suspend fun log(event: AuditEvent) {
                        raise(CommonDomainError("AUDIT_FAILED", "Audit failed"))
                    }
                }
            val badNotifications =
                object : Notifications by DbNotifications() {
                    override fun new(content: NotificationContent, recipients: List<EmployeeId>): Notification =
                        object : Notification by DbNotifications().new(content, recipients) {
                            context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
                            override suspend fun save() {
                                raise(CommonDomainError("NOTIFICATION_FAILED", "Notification failed"))
                            }
                        }
                }
            for (verification in listOf(service(audit = badAudit), service(notifications = badNotifications))) {
                assertFails { orThrow { db.transaction { verification.confirm(token, Lang.EN, null) } } }
                assertEquals(0L, count("notifications"))
                assertEquals(1L, orThrow { db.sql("SELECT count(*) AS n FROM user_email_verifications WHERE verified_at IS NULL").firstOrNull { it.asLong("n") } })
            }
            orThrow { db.transaction { service().confirm(token, Lang.EN, null) } }
            assertEquals(1L, count("notifications"))
        }

    @Test
    fun `legacy users stay out of verification and other organizations cannot see effects`() =
        runTest {
            val first = signup()
            val firstToken = token()
            val second = signup(login = "other@example.com")
            val verification = service()
            orThrow {
                db.transaction {
                    sql("DELETE FROM user_email_verifications WHERE user_id = :userId").bind("userId", second.id).execute()
                    assertFalse(verification.pending(second.id))
                    context(ctx(second)) { assertTrue(DbNotifications().of().isEmpty()) }
                    verification.confirm(firstToken, Lang.EN, null)
                    context(ctx(second)) { assertTrue(DbNotifications().of().isEmpty()) }
                    context(ctx(first)) { assertEquals(1, DbNotifications().of().size) }
                }
            }
            assertFails { orThrow { db.transaction { context(ctx(second)) { verification.resend() } } } }
            assertEquals(2L, count("org_emails"))
        }

    @Test
    fun `ownership confirms a pending email once and ignores users without a pending confirmation`() =
        runTest {
            val user = signup()
            val other = signup(login = "other@example.com")
            val verification = service()
            orThrow {
                db.transaction {
                    sql("DELETE FROM user_email_verifications WHERE user_id = :userId").bind("userId", other.id).execute()
                    verification.confirmOwnership(other.id, null)
                    verification.confirmOwnership(user.id, "192.0.2.7")
                    verification.confirmOwnership(user.id, "192.0.2.7")
                    assertFalse(verification.pending(user.id))
                }
            }
            assertEquals(1L, count("notifications"))
            assertEquals(1L, orThrow { db.sql("SELECT count(*) AS n FROM audit_logs WHERE action_type = 'auth_verify_email' AND ip_address = '192.0.2.7'").firstOrNull { it.asLong("n") } })
        }
}
