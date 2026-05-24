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
         * Используются те же переменные, что и у сервера: [POSTGRES_URL], [POSTGRES_USER], [POSTGRES_PASSWORD].
         */
        fun fromEnv(): AdminDi {
            val url = requireEnv("POSTGRES_URL")
            val user = requireEnv("POSTGRES_USER")
            val password = requireEnv("POSTGRES_PASSWORD")
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
