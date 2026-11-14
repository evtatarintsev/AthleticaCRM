package org.athletica.crm

import kotlinx.coroutines.runBlocking
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.domain.auth.DbAuth
import org.athletica.crm.domain.auth.DbUsers
import org.athletica.crm.domain.branch.DbBranches
import org.athletica.crm.domain.employees.DbEmployees
import org.athletica.crm.domain.employees.DbRoles
import org.athletica.crm.domain.org.DbOrganizations
import org.athletica.crm.domain.settings.DbUserDisplaySettings
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.storage.Database
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Singleton-контейнер PostgreSQL, общий для всех интеграционных тестов.
 * Поднимается один раз на весь тест-ран, миграции применяются сразу при старте.
 */
object TestPostgres {
    val container: PostgreSQLContainer<*> =
        PostgreSQLContainer("postgres:18").also {
            it.start()
            runMigrations(it.jdbcUrl, it.username, it.password)
        }
    val dbConfig = DatabaseConfig(container.jdbcUrl, container.username, container.password)
    val db: Database =
        createDatabase(dbConfig)

    val auth =
        PasswordHasher().let { hasher ->
            DbAuth(hasher, DbEmployees(DbUsers(hasher), DbRoles()), DbOrganizations(), DbBranches(), DbUserDisplaySettings())
        }

    /**
     * Очищает все таблицы приложения перед каждым тестом.
     * CASCADE покрывает зависимости: удаление organizations тянет employees → employee_roles,
     * удаление users тянет employees (ON DELETE CASCADE) → employee_roles.
     */
    fun truncate(): Unit =
        orThrow {
            runBlocking {
                db.sql("TRUNCATE users, organizations, channel_integrations CASCADE").execute()
            }
        }
}
