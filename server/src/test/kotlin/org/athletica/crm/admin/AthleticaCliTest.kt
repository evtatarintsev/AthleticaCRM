package org.athletica.crm.admin

import com.github.ajalt.clikt.command.test
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.PasswordHash
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.money.formatted
import org.athletica.crm.demo.DemoFixture
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.storage.asLong
import org.athletica.crm.storage.asString
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
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

    private suspend fun insertOrg(name: String, login: String): Uuid =
        orThrow {
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

    private val hasher = PasswordHasher()

    private suspend fun insertUser(login: String, password: String): Uuid =
        orThrow {
            val userId = Uuid.random()
            TestPostgres.db.sql("INSERT INTO users (id, login, password_hash) VALUES (:id, :login, :hash)")
                .bind("id", userId).bind("login", login).bind("hash", hasher.hash(password).value).execute()
            return userId
        }

    private suspend fun insertEmployee(userId: Uuid, orgName: String) =
        orThrow {
            val orgId = Uuid.random()
            TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .bind("id", orgId).bind("name", orgName).execute()
            TestPostgres.db
                .sql("INSERT INTO employees (id, user_id, org_id, name, is_owner) VALUES (:id, :userId, :orgId, :name, false)")
                .bind("id", Uuid.random()).bind("userId", userId).bind("orgId", orgId).bind("name", "Сотрудник").execute()
        }

    private suspend fun passwordHash(login: String): PasswordHash =
        orThrow {
            TestPostgres.db.sql("SELECT password_hash FROM users WHERE login = :login")
                .bind("login", login)
                .firstOrNull { PasswordHash(it.asString("password_hash")) }
                ?: error("пользователь $login не найден")
        }

    @Test
    fun `admin user reset-password заменяет пароль`() =
        runTest {
            insertUser("trainer@example.com", "старый-пароль")

            val result =
                cli().test(
                    listOf("admin", "user", "reset-password", "trainer@example.com"),
                    stdin = "новый-пароль\nновый-пароль\n",
                )

            assertEquals(0, result.statusCode, result.stderr)
            assertContains(result.stdout, "Пароль пользователя trainer@example.com изменён")
            assertFalse(result.stdout.contains("новый-пароль"))
            assertFalse(result.stderr.contains("новый-пароль"))
            val hash = passwordHash("trainer@example.com")
            assertTrue(hasher.verify("новый-пароль", hash))
            assertFalse(hasher.verify("старый-пароль", hash))
        }

    @Test
    fun `сброс пароля неизвестного пользователя завершается ненулевым кодом`() =
        runTest {
            val result =
                cli().test(
                    listOf("admin", "user", "reset-password", "nobody@example.com"),
                    stdin = "новый-пароль\nновый-пароль\n",
                )

            assertTrue(result.statusCode != 0)
            assertContains(result.stderr, "Пользователь с логином \"nobody@example.com\" не найден")
            assertEquals("", result.stdout)
        }

    @Test
    fun `пустой пароль отклоняется`() =
        runTest {
            insertUser("trainer@example.com", "старый-пароль")

            val result =
                cli().test(listOf("admin", "user", "reset-password", "trainer@example.com"), stdin = "\n\n")

            assertTrue(result.statusCode != 0)
            assertContains(result.stderr, "Пароль не может быть пустым")
            assertTrue(hasher.verify("старый-пароль", passwordHash("trainer@example.com")))
        }

    @Test
    fun `несовпадающие пароли отклоняются`() =
        runTest {
            insertUser("trainer@example.com", "старый-пароль")

            val result =
                cli().test(
                    listOf("admin", "user", "reset-password", "trainer@example.com"),
                    stdin = "новый-пароль\nдругой-пароль\n",
                )

            assertTrue(result.statusCode != 0)
            assertContains(result.stderr, "Пароли не совпадают")
            assertTrue(hasher.verify("старый-пароль", passwordHash("trainer@example.com")))
        }

    @Test
    fun `пароль нельзя передать аргументом`() =
        runTest {
            val result =
                athleticaCommand(
                    adminDi = { AdminDi { error("БД не должна открываться при ошибке разбора") } },
                    startServer = { error("сервер не должен запускаться") },
                ).test(listOf("admin", "user", "reset-password", "trainer@example.com", "пароль"))

            assertTrue(result.statusCode != 0)
            assertContains(result.stderr, "unexpected extra argument")
            assertEquals("", result.stdout)
        }

    @Test
    fun `справка admin user перечисляет reset-password`() =
        runTest {
            val result = cli().test(listOf("admin", "user", "--help"))

            assertEquals(0, result.statusCode)
            assertContains(result.stdout, "reset-password")
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

    /** Число строк таблицы [table] организации [orgId]. */
    private suspend fun rows(table: String, orgId: OrgId): Long = orThrow { TestPostgres.db.sql("SELECT count(*) AS n FROM $table WHERE org_id = :org").bind("org", orgId).firstOrNull { it.asLong("n") } ?: 0 }

    @Test
    fun `admin org seed наполняет пустую организацию и не выводит учётных данных`() =
        runTest(timeout = 5.minutes) {
            val orgId = DemoFixture.signup("Клуб Демо")
            val owner = orThrow { TestPostgres.db.sql("SELECT u.login FROM users u JOIN employees e ON e.user_id = u.id WHERE e.org_id = :org").bind("org", orgId).firstOrNull { it.asString("login") } }

            val result = cli().test(listOf("admin", "org", "seed", orgId.toString()))

            assertEquals(0, result.statusCode, result.stderr)
            assertContains(result.stdout, "Организация: Клуб Демо")
            assertContains(result.stdout, "Клиентов: 60")
            assertFalse(result.stdout.contains(checkNotNull(owner)))
            assertFalse(result.stdout.contains("парол", ignoreCase = true) || result.stdout.contains("password", ignoreCase = true))
            assertEquals(60, rows("clients", orgId))
            assertEquals(1, orThrow { TestPostgres.db.sql("SELECT count(*) AS n FROM employees WHERE org_id = :org AND user_id IS NOT NULL").bind("org", orgId).firstOrNull { it.asLong("n") } })
        }

    @Test
    fun `admin org seed отказывает на непустой организации`() =
        runTest(timeout = 5.minutes) {
            val orgId = DemoFixture.signup("Клуб Занят")
            orThrow {
                TestPostgres.db.sql("INSERT INTO clients (id, org_id, name, gender) VALUES (:id, :org, 'Клиент', 'MALE')").bind("id", Uuid.random()).bind("org", orgId).execute()
            }

            val result = cli().test(listOf("admin", "org", "seed", orgId.toString(), "--size=medium", "--seed=42"))

            assertTrue(result.statusCode != 0)
            assertContains(result.stderr, "наполнить можно только новую организацию")
            assertEquals(1, rows("clients", orgId))
            assertEquals(0, rows("groups", orgId))
        }

    @Test
    fun `admin org seed с недопустимым профилем падает до подключения к БД`() =
        runTest {
            val noDb = AdminDi { error("подключение к БД не должно открываться") }

            val result = athleticaCommand(adminDi = { noDb }).test(listOf("admin", "org", "seed", "Клуб", "--size=huge"))

            assertTrue(result.statusCode != 0)
            assertContains(result.stderr, "--size")
            assertEquals("", result.stdout)
        }
}
