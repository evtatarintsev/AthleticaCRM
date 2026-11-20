package org.athletica.crm.domain.ledger

import org.athletica.crm.TestPostgres
import org.junit.Before
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Ограничения главной книги проверяются при коммите, независимо от доменного кода. */
class LedgerSchemaTest {
    @Before
    fun setUp() = TestPostgres.truncate()

    private fun connection(): Connection = TestPostgres.container.let { DriverManager.getConnection(it.jdbcUrl, it.username, it.password) }

    private fun Connection.execute(sql: String) {
        createStatement().use { it.execute(sql) }
    }

    private fun Connection.scalar(sql: String): String =
        createStatement().use { statement ->
            statement.executeQuery(sql).use { rows ->
                check(rows.next())
                rows.getString(1)
            }
        }

    private fun Connection.prepareEntry(): String {
        execute("INSERT INTO organizations (name) VALUES ('Ledger test')")
        val org = scalar("SELECT id FROM organizations")
        execute("INSERT INTO ledger_accounts (org_id, kind) VALUES ('$org', 'revenue'), ('$org', 'adjustment')")
        return org
    }

    private fun Connection.entry(org: String, amounts: String): String {
        val entry = scalar("INSERT INTO ledger_transactions (org_id, kind, occurred_on) VALUES ('$org', 'adjustment', CURRENT_DATE) RETURNING id")
        execute(
            """
            INSERT INTO ledger_postings (transaction_id, account_id, amount)
            SELECT '$entry', a.id, v.amount FROM
            (SELECT id, row_number() OVER (ORDER BY id) AS n FROM ledger_accounts WHERE org_id = '$org') a
            JOIN (VALUES $amounts) v(n, amount) ON a.n = v.n
            """.trimIndent(),
        )
        return entry
    }

    @Test
    fun `миграция создаёт таблицы и сбалансированная запись коммитится`() {
        connection().use { db ->
            val org = db.prepareEntry()
            db.autoCommit = false
            val entry = db.entry(org, "(1, 4500), (2, -4500)")
            db.commit()
            assertEquals("2", db.scalar("SELECT count(*) FROM ledger_postings WHERE transaction_id = '$entry'"))
        }
    }

    @Test
    fun `несбалансированная запись отклоняется при коммите`() {
        connection().use { db ->
            val org = db.prepareEntry()
            db.autoCommit = false
            db.entry(org, "(1, 5000), (2, -4500)")
            assertEquals("23514", assertFailsWith<SQLException> { db.commit() }.sqlState)
            db.rollback()
            assertEquals("0", db.scalar("SELECT count(*) FROM ledger_transactions"))
        }
    }

    @Test
    fun `транзакция без проводок отклоняется`() {
        connection().use { db ->
            val org = db.prepareEntry()
            db.autoCommit = false
            db.execute("INSERT INTO ledger_transactions (org_id, kind, occurred_on) VALUES ('$org', 'adjustment', CURRENT_DATE)")
            assertEquals("23514", assertFailsWith<SQLException> { db.commit() }.sqlState)
            db.rollback()
        }
    }

    @Test
    fun `счёт кассы требует владельца`() {
        connection().use { db ->
            val org = db.prepareEntry()
            assertEquals("23514", assertFailsWith<SQLException> { db.execute("INSERT INTO ledger_accounts (org_id, kind) VALUES ('$org', 'cash')") }.sqlState)
        }
    }

    @Test
    fun `один счёт вида на организацию`() {
        connection().use { db ->
            val org = db.prepareEntry()
            assertEquals("23505", assertFailsWith<SQLException> { db.execute("INSERT INTO ledger_accounts (org_id, kind) VALUES ('$org', 'revenue')") }.sqlState)
        }
    }

    @Test
    fun `кассовая проводка требует статью`() {
        connection().use { db ->
            val org = db.prepareEntry()
            db.execute("INSERT INTO cash_accounts (org_id, name, kind) VALUES ('$org', 'Cash', 'cash')")
            db.execute("INSERT INTO ledger_accounts (org_id, kind, cash_account_id) SELECT '$org', 'cash', id FROM cash_accounts")
            db.autoCommit = false
            val entry = db.scalar("INSERT INTO ledger_transactions (org_id, kind, occurred_on) VALUES ('$org', 'income', CURRENT_DATE) RETURNING id")
            db.execute("INSERT INTO ledger_postings (transaction_id, account_id, amount) SELECT '$entry', id, CASE WHEN kind = 'cash' THEN 100 ELSE -100 END FROM ledger_accounts WHERE kind IN ('cash', 'revenue')")
            assertEquals("23514", assertFailsWith<SQLException> { db.commit() }.sqlState)
            db.rollback()
        }
    }

    @Test
    fun `проводки чужой организации отклоняются`() {
        connection().use { db ->
            val org = db.prepareEntry()
            db.execute("INSERT INTO organizations (name) VALUES ('Other')")
            val other = db.scalar("SELECT id FROM organizations WHERE id <> '$org'")
            db.autoCommit = false
            val entry = db.scalar("INSERT INTO ledger_transactions (org_id, kind, occurred_on) VALUES ('$other', 'adjustment', CURRENT_DATE) RETURNING id")
            db.execute("INSERT INTO ledger_postings (transaction_id, account_id, amount) SELECT '$entry', id, CASE WHEN kind = 'revenue' THEN 100 ELSE -100 END FROM ledger_accounts")
            assertEquals("23514", assertFailsWith<SQLException> { db.commit() }.sqlState)
            db.rollback()
        }
    }

    @Test
    fun `записанные транзакции и проводки неизменяемы`() {
        connection().use { db ->
            val org = db.prepareEntry()
            db.autoCommit = false
            val entry = db.entry(org, "(1, 4500), (2, -4500)")
            db.commit()
            db.autoCommit = true
            assertEquals("23514", assertFailsWith<SQLException> { db.execute("UPDATE ledger_postings SET amount = 1 WHERE transaction_id = '$entry'") }.sqlState)
            assertEquals("23514", assertFailsWith<SQLException> { db.execute("DELETE FROM ledger_transactions WHERE id = '$entry'") }.sqlState)
        }
    }
}
