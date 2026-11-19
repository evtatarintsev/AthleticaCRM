package org.athletica.crm.domain.auth

import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.storage.asLong
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class EmailVerificationMigrationTest {
    @Before
    fun setUp() = TestPostgres.truncate()

    @Test
    fun `existing accounts are not marked verified and constraints reject duplicate tokens`() =
        runTest {
            orThrow {
                val db = TestPostgres.db
                db.sql("INSERT INTO users (login, password_hash) VALUES ('legacy', 'hash'), ('another', 'hash')").execute()
                db.sql("INSERT INTO organizations (name) VALUES ('Legacy')").execute()
                assertEquals(0L, db.sql("SELECT count(*) AS n FROM user_email_verifications").firstOrNull { it.asLong("n") })
                val insert =
                    """
                    INSERT INTO user_email_verifications (user_id, org_id, email, token_hash, expires_at, last_sent_at)
                    SELECT u.id, o.id, u.login, 'token-hash', now() + interval '24 hours', now()
                    FROM users u CROSS JOIN organizations o WHERE u.login = :login
                    """.trimIndent()
                db.sql(insert).bind("login", "legacy").execute()
                assertFails { orThrow { db.sql(insert).bind("login", "another").execute() } }
                assertFails { orThrow { db.sql(insert.replace("'token-hash'", "'new-hash'")).bind("login", "legacy").execute() } }
                db.sql("DELETE FROM users WHERE login = 'legacy'").execute()
                assertEquals(0L, db.sql("SELECT count(*) AS n FROM user_email_verifications").firstOrNull { it.asLong("n") })
            }
        }
}
