package org.athletica.crm.domain.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.settings.DisplaySettings
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Тест миграции `0090-reset-client-list-columns`: сброс сохранённых колонок списка клиентов. */
class ResetClientListColumnsMigrationTest {
    /** Настройки до миграции: свой набор колонок клиентов, сохранённый вид и колонки групп. */
    private val before =
        """
        {"clients": {"columns": ["phone", "birthday", "groups"],
                     "savedViews": [{"id": "v1", "name": "Должники", "filterJson": {"hasDebt": true}}]},
         "groups": {"columns": ["coach"]}}
        """.trimIndent()

    @Test
    fun `сохранённые колонки клиентов сбрасываются на умолчание, остальное на месте`() {
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
                    val index = pending.indexOfFirst { it.id == "0090-reset-client-list-columns" }
                    assertTrue(index > 0)
                    liquibase.update(index, "")
                    connection.createStatement().use { statement ->
                        statement.execute("SET search_path TO $schema")
                        statement.execute("INSERT INTO users (id, login, password_hash) VALUES ('00000000-0000-7000-8000-000000000001', 'u', 'h')")
                        statement.execute(
                            "INSERT INTO user_display_settings (user_id, settings) VALUES ('00000000-0000-7000-8000-000000000001', '$before'::jsonb)",
                        )
                    }
                    liquibase.update("")
                    val after =
                        connection.createStatement().use { statement ->
                            statement.executeQuery("SELECT settings FROM user_display_settings").use { rows ->
                                assertTrue(rows.next())
                                rows.getString(1)
                            }
                        }
                    val settings = Json.decodeFromString<DisplaySettings>(after)
                    assertEquals(listOf("groups", "memberships", "balance"), settings.clients.columns)
                    assertEquals(listOf("v1"), settings.clients.savedViews.map { it.id })
                    assertEquals(listOf("coach"), settings.groups.columns)
                    val beforeJson = Json.parseToJsonElement(before).jsonObject
                    val afterJson = Json.parseToJsonElement(after).jsonObject
                    assertEquals(
                        JsonObject(beforeJson.getValue("clients").jsonObject - "columns"),
                        afterJson.getValue("clients"),
                    )
                }
            } finally {
                DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use { cleanup ->
                    cleanup.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") }
                }
            }
        }
    }
}
