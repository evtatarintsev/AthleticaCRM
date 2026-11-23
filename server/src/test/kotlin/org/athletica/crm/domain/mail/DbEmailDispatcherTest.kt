package org.athletica.crm.domain.mail

import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.SystemContexts
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.storage.asInstantOrNull
import org.athletica.crm.storage.asInt
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.infra.mail.Email
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Тесты состояний исходящих писем и диспетчера [DbEmailDispatcher]. */
class DbEmailDispatcherTest {
    private val orgId = OrgId.new()
    private val emails = DbOrgEmails()

    /** Почтовый ящик-заглушка: запоминает отправленные письма и при [fail] бросает исключение. */
    private class FakeMailbox(private val fail: Boolean = false) : Mailbox {
        /** Письма, которые пытались отправить. */
        val sent = mutableListOf<Email>()

        override suspend fun send(email: Email) {
            sent += email
            if (fail) {
                throw IllegalStateException("smtp down")
            }
        }
    }

    /** Строка `org_emails` в виде, удобном для проверок. */
    private data class Stored(
        val status: String,
        val attempts: Int,
        val sentAt: Boolean,
        val error: String?,
    )

    @Before
    fun setUp() =
        runTest {
            TestPostgres.truncate()
            orThrow {
                TestPostgres.db.transaction {
                    sql("INSERT INTO organizations (id, name) VALUES (:id, 'Mail test')").bind("id", orgId).execute()
                    context(SystemContexts.forOrg(orgId)) {
                        emails.schedule(listOf(EmailAddress("owner@example.com")), "Welcome", "Text", "<p>Text</p>")
                    }
                }
            }
        }

    private fun dispatcher(mailbox: Mailbox, maxAttempts: Int = 5) = DbEmailDispatcher(TestPostgres.db, emails, mailbox, maxAttempts = maxAttempts, retryDelay = Duration.ZERO)

    private suspend fun stored(): Stored =
        orThrow {
            TestPostgres.db.sql("SELECT status, attempt_count, sent_at, last_error FROM org_emails").firstOrNull {
                Stored(it.asString("status"), it.asInt("attempt_count"), it.asInstantOrNull("sent_at") != null, it.asStringOrNull("last_error"))
            }!!
        }

    @Test
    fun `sent email is marked sent and never sent again`() =
        runTest {
            val mailbox = FakeMailbox()
            val dispatcher = dispatcher(mailbox)

            dispatcher.pollOnce()
            dispatcher.pollOnce()

            assertEquals(1, mailbox.sent.size)
            assertEquals(Stored("SENT", 1, true, null), stored())
        }

    @Test
    fun `transient failure keeps email pending until attempts run out, then fails without resending`() =
        runTest {
            val mailbox = FakeMailbox(fail = true)
            val dispatcher = dispatcher(mailbox, maxAttempts = 2)

            dispatcher.pollOnce()
            assertEquals(Stored("PENDING", 1, false, null), stored())

            dispatcher.pollOnce()
            assertEquals(Stored("FAILED", 2, false, "smtp down"), stored())

            dispatcher.pollOnce()
            assertEquals(2, mailbox.sent.size)
        }

    @Test
    fun `claimed email is not claimed again while the lease holds`() =
        runTest {
            orThrow {
                val first = TestPostgres.db.transaction { emails.claimPending(10, 1.minutes) }
                val second = TestPostgres.db.transaction { emails.claimPending(10, 1.minutes) }

                assertEquals(OrgEmailState.Pending, first.single().state)
                assertTrue(second.isEmpty())
            }
        }

    @Test
    fun `transitions apply only to a pending email`() =
        runTest {
            orThrow {
                val email = TestPostgres.db.transaction { emails.claimPending(10, Duration.ZERO) }.single()
                TestPostgres.db.transaction { email.markSent() }
                TestPostgres.db.transaction { email.markFailed("late error") }
                TestPostgres.db.transaction { email.retryLater(Duration.ZERO) }

                val result = stored()
                assertEquals("SENT", result.status)
                assertEquals(1, result.attempts)
                assertNull(result.error)
                assertTrue(TestPostgres.db.transaction { emails.claimPending(10, Duration.ZERO) }.isEmpty())
            }
        }
}
