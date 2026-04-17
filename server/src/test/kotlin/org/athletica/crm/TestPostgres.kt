package org.athletica.crm

import kotlinx.coroutines.runBlocking
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

    val db: Database =
        createDatabase(container.jdbcUrl, container.username, container.password)

    /**
     * Очищает все таблицы приложения перед каждым тестом.
     * CASCADE покрывает зависимости: удаление organizations тянет employees → employee_roles,
     * удаление users тянет employees (ON DELETE CASCADE) → employee_roles.
     */
    fun truncate(): Unit =
        runBlocking {
            db.sql("TRUNCATE users, organizations CASCADE").execute()
        }
}
