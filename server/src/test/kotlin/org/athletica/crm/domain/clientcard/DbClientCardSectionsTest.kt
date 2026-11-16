package org.athletica.crm.domain.clientcard

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.athletica.crm.TestPostgres
import org.athletica.crm.domain.memberships.MembershipFixture
import org.athletica.crm.storage.asLong
import org.junit.Before
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DbClientCardSectionsTest {
    private val first = MembershipFixture()
    private val second = MembershipFixture()
    private val settings = DbClientCardSectionSettings()

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            first.setUp()
            second.setUp()
        }
    }

    @Test
    fun `defaults persistence isolation and hiding preserves notes`() =
        runTest {
            assertEquals(DbClientCardSections(), first.tx { settings.current() })
            val client = first.insertClient()
            first.tx {
                TestPostgres.db.sql("INSERT INTO client_notes (org_id, client_id, author_id, text) SELECT :org, :client, :author, 'Note' FROM generate_series(1, 3)")
                    .bind("org", first.orgId).bind("client", client).bind("author", first.employeeId).execute()
                settings.current().withNew(true, true, true, true).save()
                settings.current().withNew(true, false, false, false).save()
                assertEquals(DbClientCardSections(true, false, false, false), settings.current())
                settings.current().withNew(true, true, false, false).save()
                assertEquals(3L, TestPostgres.db.sql("SELECT count(*) AS n FROM client_notes WHERE client_id = :id").bind("id", client).firstOrNull { it.asLong("n") })
            }
            assertEquals(DbClientCardSections(), second.tx { settings.current() })
        }

    @Test
    fun `migration enables existing organizations only`() {
        val container = TestPostgres.container
        val schema = "migration_" + UUID.randomUUID().toString().replace("-", "")
        DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use { connection ->
            connection.createStatement().use { it.execute("CREATE SCHEMA $schema") }
            try {
                val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(connection))
                database.defaultSchemaName = schema
                database.liquibaseSchemaName = schema
                Liquibase("db/changelog/db.changelog-master.yaml", ClassLoaderResourceAccessor(), database).use { liquibase ->
                    val pending = liquibase.listUnrunChangeSets(null, null)
                    val index = pending.indexOfFirst { it.id == "0084-client-card-sections" }
                    assertTrue(index > 0)
                    liquibase.update(index, "")
                    connection.createStatement().use { statement ->
                        statement.execute("SET search_path TO $schema")
                        statement.execute("INSERT INTO organizations (name, timezone) VALUES ('Existing', 'UTC')")
                    }
                    liquibase.update("")
                    connection.createStatement().use { statement ->
                        statement.executeQuery("SELECT tasks, notes, chat, documents FROM client_card_sections").use { rows ->
                            assertTrue(rows.next())
                            (1..4).forEach { assertTrue(rows.getBoolean(it)) }
                            assertFalse(rows.next())
                        }
                        statement.execute("INSERT INTO organizations (name, timezone) VALUES ('New', 'UTC')")
                        statement.executeQuery("SELECT count(*) FROM organizations o LEFT JOIN client_card_sections c ON c.org_id = o.id WHERE o.name = 'New' AND c.org_id IS NULL").use { rows ->
                            assertTrue(rows.next())
                            assertEquals(1, rows.getInt(1))
                        }
                    }
                }
            } finally {
                DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use { cleanup ->
                    cleanup.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") }
                }
            }
        }
    }
}
