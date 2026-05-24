package org.athletica.crm.admin

import org.athletica.crm.createDatabase
import org.athletica.crm.domain.orgbalance.DbOrgBalances
import org.athletica.crm.domain.orgbalance.OrgBalances
import org.athletica.crm.storage.Database

/** Минимальный контейнер зависимостей для admin-cli. */
class AdminDi(
    val database: Database,
    val orgBalances: OrgBalances,
    val orgSearch: OrgSearch,
) {
    companion object {
        /**
         * Создаёт [AdminDi] из переменных окружения.
         * Используются те же переменные, что и у сервера: [DATABASE_URL], [DATABASE_USER], [DATABASE_PASSWORD].
         */
        fun fromEnv(): AdminDi {
            val url = requireEnv("DATABASE_URL")
            val user = requireEnv("DATABASE_USER")
            val password = requireEnv("DATABASE_PASSWORD")
            val database = createDatabase(url, user, password)
            return AdminDi(
                database = database,
                orgBalances = DbOrgBalances(),
                orgSearch = OrgSearch(database),
            )
        }

        private fun requireEnv(name: String): String =
            System.getenv(name)
                ?: error("Переменная окружения $name не задана")
    }
}
