package org.athletica.crm.admin

import com.github.ajalt.clikt.command.test
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.money.formatted
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Тесты поверхности командной строки: разбор аргументов, маршрутизация по дереву команд,
 * коды возврата и разделение потоков вывода.
 */
class AthleticaCliTest {
    private val adminDi = AdminDi { TestPostgres.db }

    private var serverStarts = 0

    private fun cli() = athleticaCommand(adminDi = { adminDi }, startServer = { serverStarts++ })

    @Before
    fun setUp() {
        TestPostgres.truncate()
        serverStarts = 0
    }

    private suspend fun insertOrg(name: String, login: String): Uuid {
        val orgId = Uuid.random()
        val userId = Uuid.random()
        TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
            .bind("id", orgId).bind("name", name).execute()
        TestPostgres.db.sql("INSERT INTO users (id, login, password_hash) VALUES (:id, :login, :hash)")
            .bind("id", userId).bind("login", login).bind("hash", "hash").execute()
        TestPostgres.db
            .sql("INSERT INTO employees (id, user_id, org_id, name, is_owner) VALUES (:id, :userId, :orgId, :name, true)")
            .bind("id", Uuid.random()).bind("userId", userId).bind("orgId", orgId).bind("name", "Владелец").execute()
        return orgId
    }

    @Test
    fun `serve запускает веб-сервер`() =
        runTest {
            val result = cli().test(listOf("serve"))

            assertEquals(0, result.statusCode)
            assertEquals(1, serverStarts)
        }

    @Test
    fun `admin org find выводит найденные организации`() =
        runTest {
            val orgId = insertOrg("Клуб Атлет", "owner@example.com")

            val result = cli().test(listOf("admin", "org", "find", "Атлет"))

            assertEquals(0, result.statusCode)
            assertContains(result.stdout, "Результаты для \"Атлет\":")
            assertContains(result.stdout, orgId.toString())
            assertContains(result.stdout, "owner@example.com")
            assertEquals(0, serverStarts)
        }

    @Test
    fun `admin org find без совпадений завершается нулевым кодом`() =
        runTest {
            val result = cli().test(listOf("admin", "org", "find", "нетсовпадений"))

            assertEquals(0, result.statusCode)
            assertContains(result.stdout, "Не найдено: нетсовпадений")
        }

    @Test
    fun `admin org find обрабатывает каждый запрос отдельно`() =
        runTest {
            insertOrg("Клуб Атлет", "owner@example.com")

            val result = cli().test(listOf("admin", "org", "find", "нетсовпадений", "Атлет"))

            assertEquals(0, result.statusCode)
            assertContains(result.stdout, "Не найдено: нетсовпадений")
            assertContains(result.stdout, "Результаты для \"Атлет\":")
        }

    @Test
    fun `admin org credit увеличивает баланс организации`() =
        runTest {
            insertOrg("Клуб Атлет", "owner@example.com")

            val result = cli().test(listOf("admin", "org", "credit", "Атлет", "1000", "--description=Пополнение"))

            assertEquals(0, result.statusCode)
            assertContains(result.stdout, "Корректировка: +${Money(100_000, Currency.RUB).formatted}")
            assertContains(result.stdout, "Новый баланс:  ${Money(100_000, Currency.RUB).formatted}")
        }

    @Test
    fun `admin org debit уменьшает баланс организации`() =
        runTest {
            val orgId = insertOrg("Клуб Атлет", "owner@example.com")
            cli().test(listOf("admin", "org", "credit", orgId.toString(), "1000", "--description=Пополнение"))

            val result = cli().test(listOf("admin", "org", "debit", orgId.toString(), "250.50", "--description=Списание"))

            assertEquals(0, result.statusCode)
            assertContains(result.stdout, "Корректировка: ${Money(-25_050, Currency.RUB).formatted}")
            assertContains(result.stdout, "Новый баланс:  ${Money(74_950, Currency.RUB).formatted}")
        }

    @Test
    fun `корректировка без описания отклоняется разбором аргументов`() =
        runTest {
            val result = cli().test(listOf("admin", "org", "credit", "Атлет", "1000"))

            assertTrue(result.statusCode != 0)
            assertContains(result.stderr, "--description")
            assertEquals("", result.stdout)
        }

    @Test
    fun `неизвестная подкоманда отклоняется`() =
        runTest {
            val result = cli().test(listOf("админ"))

            assertTrue(result.statusCode != 0)
            assertContains(result.stderr, "no such subcommand")
            assertEquals("", result.stdout)
            assertEquals(0, serverStarts)
        }

    @Test
    fun `корректировка по UUID организации`() =
        runTest {
            val orgId = insertOrg("Клуб Атлет", "owner@example.com")

            val result = cli().test(listOf("admin", "org", "credit", orgId.toString(), "10", "--description=Пополнение"))

            assertEquals(0, result.statusCode)
            assertContains(result.stdout, "Организация: Клуб Атлет ($orgId)")
        }

    @Test
    fun `неоднозначное название отклоняется со списком организаций`() =
        runTest {
            val first = insertOrg("Клуб Атлет", "one@example.com")
            val second = insertOrg("Клуб Атлетика", "two@example.com")

            val result = cli().test(listOf("admin", "org", "credit", "Клуб", "10", "--description=Пополнение"))

            assertTrue(result.statusCode != 0)
            assertContains(result.stderr, first.toString())
            assertContains(result.stderr, second.toString())
            assertEquals("", result.stdout)
        }

    @Test
    fun `ненайденная организация завершается ненулевым кодом`() =
        runTest {
            val result = cli().test(listOf("admin", "org", "credit", "нетсовпадений", "10", "--description=Пополнение"))

            assertTrue(result.statusCode != 0)
            assertContains(result.stderr, "Организация не найдена: нетсовпадений")
            assertEquals("", result.stdout)
        }

    @Test
    fun `справка выводится без параметров подключения к БД`() =
        runTest {
            val result =
                athleticaCommand(
                    adminDi = { error("контейнер зависимостей не должен создаваться для справки") },
                    startServer = { error("сервер не должен запускаться для справки") },
                ).test(listOf("--help"))

            assertEquals(0, result.statusCode)
            assertContains(result.stdout, "serve")
            assertContains(result.stdout, "admin")
        }
}
