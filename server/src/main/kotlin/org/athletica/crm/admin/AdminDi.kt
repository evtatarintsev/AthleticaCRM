package org.athletica.crm.admin

import com.github.ajalt.clikt.core.CliktError
import org.athletica.crm.createDatabase
import org.athletica.crm.demo.DemoData
import org.athletica.crm.domain.auth.DbUsers
import org.athletica.crm.domain.employees.DbEmployees
import org.athletica.crm.domain.employees.DbRoles
import org.athletica.crm.domain.employees.Employees
import org.athletica.crm.domain.events.FailedDomainEvents
import org.athletica.crm.domain.orgbalance.DbOrgBalances
import org.athletica.crm.domain.orgbalance.OrgBalances
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.storage.Database

/**
 * Минимальный контейнер зависимостей административных команд.
 * Подключение к БД создаётся при первом обращении к [database], поэтому регистрация
 * контейнера в дереве команд не открывает соединение и не требует переменных окружения.
 * [databaseFactory] — отложенное создание подключения к БД.
 */
class AdminDi(
    databaseFactory: () -> Database,
) {
    /** Подключение к БД; создаётся при первом обращении. */
    val database: Database by lazy(databaseFactory)

    /** Балансы организаций. */
    val orgBalances: OrgBalances = DbOrgBalances()

    /** Неуспешные доменные события всех организаций. */
    val failedEvents: FailedDomainEvents by lazy { FailedDomainEvents(database) }

    /** Поиск организаций без привязки к организации запроса. */
    val orgSearch: OrgSearch by lazy { OrgSearch(database) }

    /** Сотрудники организаций: контекст владельца для наполнения. */
    val employees: Employees by lazy { DbEmployees(DbUsers(PasswordHasher()), DbRoles()) }

    /** Наполнитель пустой организации демонстрационными данными. */
    val demoData: DemoData by lazy { DemoData() }

    /** Пароли пользователей всех организаций. */
    val userPasswords: UserPasswords by lazy { UserPasswords(database, PasswordHasher()) }

    companion object {
        /**
         * Создаёт [AdminDi], читающий параметры подключения из переменных окружения
         * `POSTGRES_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD` при первом обращении к БД.
         */
        fun fromEnv(): AdminDi =
            AdminDi {
                createDatabase(
                    requireEnv("POSTGRES_URL"),
                    requireEnv("POSTGRES_USER"),
                    requireEnv("POSTGRES_PASSWORD"),
                )
            }

        private fun requireEnv(name: String): String =
            System.getenv(name)
                ?: throw CliktError("Переменная окружения $name не задана")
    }
}
