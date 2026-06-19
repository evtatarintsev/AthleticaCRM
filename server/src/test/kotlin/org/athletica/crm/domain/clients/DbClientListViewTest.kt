package org.athletica.crm.domain.clients

import arrow.core.Either
import arrow.core.raise.either
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.DateRange
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Gender
import org.athletica.crm.core.Lang
import org.athletica.crm.core.contacts.ContactType
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.entityids.toGroupId
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.storage.asUuid
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Тесты read-проекции списка клиентов: фильтры, сортировка, поиск и пагинация. */
class DbClientListViewTest {
    @Before
    fun setUp() = TestPostgres.truncate()

    private val baseInstant = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private suspend fun insertOrg(name: String = "Test Org"): Uuid {
        val orgId = Uuid.generateV7()
        TestPostgres.db
            .sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
            .bind("id", orgId)
            .bind("name", name)
            .execute()
        return orgId
    }

    private suspend fun insertClient(
        orgId: Uuid,
        name: String,
        gender: String = "MALE",
        birthday: LocalDate? = null,
        archived: Boolean = false,
    ): ClientId {
        val clientId = ClientId.new()
        TestPostgres.db
            .sql(
                """
                INSERT INTO clients (id, org_id, name, gender, birthday, state)
                VALUES (:id, :orgId, :name, :gender::gender, :birthday, :state::client_state)
                """.trimIndent(),
            )
            .bind("id", clientId)
            .bind("orgId", orgId)
            .bind("name", name)
            .bind("gender", gender)
            .bind("birthday", birthday)
            .bind("state", if (archived) "ARCHIVED" else "ACTIVE")
            .execute()
        return clientId
    }

    private suspend fun ensureBranch(orgId: Uuid): Uuid {
        val existing =
            TestPostgres.db
                .sql("SELECT id FROM branches WHERE org_id = :orgId LIMIT 1")
                .bind("orgId", orgId)
                .firstOrNull { it.asUuid("id") }
        if (existing != null) return existing
        val branchId = Uuid.generateV7()
        TestPostgres.db
            .sql("INSERT INTO branches (id, org_id, name) VALUES (:id, :orgId, :name)")
            .bind("id", branchId)
            .bind("orgId", orgId)
            .bind("name", "Основной")
            .execute()
        return branchId
    }

    private suspend fun insertGroup(orgId: Uuid, name: String): Uuid {
        val groupId = Uuid.generateV7()
        val branchId = ensureBranch(orgId)
        TestPostgres.db
            .sql("INSERT INTO groups (id, org_id, name, branch_id) VALUES (:id, :orgId, :name, :branchId)")
            .bind("id", groupId)
            .bind("orgId", orgId)
            .bind("name", name)
            .bind("branchId", branchId)
            .execute()
        return groupId
    }

    private suspend fun addClientToGroup(clientId: ClientId, groupId: Uuid) {
        TestPostgres.db
            .sql("INSERT INTO enrollments (client_id, group_id) VALUES (:clientId, :groupId)")
            .bind("clientId", clientId)
            .bind("groupId", groupId)
            .execute()
    }

    /** Создаёт пользователя и сотрудника-владельца организации; возвращает id сотрудника. */
    private suspend fun insertEmployee(orgId: Uuid): EmployeeId {
        val userId = UserId.new()
        val employeeId = EmployeeId.new()
        TestPostgres.db
            .sql("INSERT INTO users (id, login, password_hash) VALUES (:id, :login, :hash)")
            .bind("id", userId)
            .bind("login", "u$userId@example.com")
            .bind("hash", "hash")
            .execute()
        TestPostgres.db
            .sql("INSERT INTO employees (id, user_id, org_id, name, is_owner) VALUES (:id, :userId, :orgId, :name, true)")
            .bind("id", employeeId)
            .bind("userId", userId)
            .bind("orgId", orgId)
            .bind("name", "Admin")
            .execute()
        return employeeId
    }

    private suspend fun insertJournalEntry(
        orgId: Uuid,
        clientId: ClientId,
        performedBy: EmployeeId,
        balanceAfter: Long,
        createdAt: Instant,
        operationType: String = "admin_credit",
    ) {
        TestPostgres.db
            .sql(
                """
                INSERT INTO client_balance_journal (org_id, client_id, amount, balance_after, operation_type, performed_by, created_at)
                VALUES (:orgId, :clientId, :amount, :balanceAfter, :type::balance_operation_type, :performedBy, :createdAt)
                """.trimIndent(),
            )
            .bind("orgId", orgId)
            .bind("clientId", clientId)
            .bind("amount", Money(balanceAfter, Currency.RUB))
            .bind("balanceAfter", Money(balanceAfter, Currency.RUB))
            .bind("type", operationType)
            .bind("performedBy", performedBy)
            .bind("createdAt", createdAt)
            .execute()
    }

    private suspend fun insertContact(
        orgId: Uuid,
        clientId: ClientId,
        type: String,
        value: String,
    ) {
        TestPostgres.db
            .sql(
                """
                INSERT INTO client_contacts (id, org_id, client_id, type, value)
                VALUES (:id, :orgId, :clientId, :type, :value)
                """.trimIndent(),
            )
            .bind("id", Uuid.generateV7())
            .bind("orgId", orgId)
            .bind("clientId", clientId)
            .bind("type", type)
            .bind("value", value)
            .execute()
    }

    private fun ctx(orgId: Uuid) =
        EmployeeRequestContext(
            lang = Lang.EN,
            userId = UserId.new(),
            orgId = OrgId(orgId),
            branchId = BranchId.new(),
            employeeId = EmployeeId.new(),
            username = "user@example.com",
            clientIp = "127.0.0.1",
            currency = Currency.RUB,
            permission = EmployeePermission(),
        )

    private suspend fun page(orgId: Uuid, query: ClientListQuery): ClientListPage {
        val result =
            either {
                TestPostgres.db.transaction {
                    context(ctx(orgId)) {
                        DbClientListView().page(query)
                    }
                }
            }
        return assertIs<Either.Right<ClientListPage>>(result).value
    }

    @Test
    fun `пустая выборка если клиентов нет`() =
        runTest {
            val orgId = insertOrg()
            val result = page(orgId, ClientListQuery())
            assertTrue(result.rows.isEmpty())
            assertEquals(0, result.total)
        }

    @Test
    fun `возвращает клиентов организации с total`() =
        runTest {
            val orgId = insertOrg()
            insertClient(orgId, "Анна")
            insertClient(orgId, "Борис")
            val result = page(orgId, ClientListQuery())
            assertEquals(2, result.rows.size)
            assertEquals(2, result.total)
        }

    @Test
    fun `изоляция между организациями`() =
        runTest {
            val org1 = insertOrg("Org 1")
            val org2 = insertOrg("Org 2")
            insertClient(org1, "Клиент 1")
            insertClient(org2, "Клиент 2")
            val result = page(org1, ClientListQuery())
            assertEquals(1, result.rows.size)
            assertEquals("Клиент 1", result.rows.single().name)
        }

    @Test
    fun `фильтр по полу`() =
        runTest {
            val orgId = insertOrg()
            insertClient(orgId, "Муж", gender = "MALE")
            insertClient(orgId, "Жен", gender = "FEMALE")
            val result = page(orgId, ClientListQuery(gender = Gender.FEMALE))
            assertEquals(1, result.rows.size)
            assertEquals("Жен", result.rows.single().name)
        }

    @Test
    fun `фильтр hasDebt по последнему балансу`() =
        runTest {
            val orgId = insertOrg()
            val performer = insertEmployee(orgId)
            val debtor = insertClient(orgId, "Должник")
            val solvent = insertClient(orgId, "Платёжеспособный")
            val noJournal = insertClient(orgId, "Без журнала")
            // у должника последняя запись отрицательная, хотя ранее был плюс
            insertJournalEntry(orgId, debtor, performer, balanceAfter = 5_000, createdAt = baseInstant)
            insertJournalEntry(orgId, debtor, performer, balanceAfter = -3_000, createdAt = baseInstant + 1.seconds)
            insertJournalEntry(orgId, solvent, performer, balanceAfter = 10_000, createdAt = baseInstant)

            val result = page(orgId, ClientListQuery(hasDebt = true))
            assertEquals(1, result.rows.size)
            assertEquals("Должник", result.rows.single().name)
            assertTrue(result.rows.single().balance.isNegative)
            // noJournal без записей трактуется как 0 и не попадает
            assertTrue(result.rows.none { it.id == noJournal })
            assertTrue(result.rows.none { it.id == solvent })
        }

    @Test
    fun `фильтр noGroup`() =
        runTest {
            val orgId = insertOrg()
            val withGroup = insertClient(orgId, "С группой")
            insertClient(orgId, "Без группы")
            val groupId = insertGroup(orgId, "Группа")
            addClientToGroup(withGroup, groupId)

            val result = page(orgId, ClientListQuery(noGroup = true))
            assertEquals(1, result.rows.size)
            assertEquals("Без группы", result.rows.single().name)
        }

    @Test
    fun `фильтр по группе`() =
        runTest {
            val orgId = insertOrg()
            val member = insertClient(orgId, "Участник")
            insertClient(orgId, "Не участник")
            val groupId = insertGroup(orgId, "Группа")
            addClientToGroup(member, groupId)

            val result = page(orgId, ClientListQuery(groupId = groupId.toGroupId()))
            assertEquals(1, result.rows.size)
            assertEquals("Участник", result.rows.single().name)
        }

    @Test
    fun `архивные и активные разделены`() =
        runTest {
            val orgId = insertOrg()
            insertClient(orgId, "Активный")
            insertClient(orgId, "Архивный", archived = true)

            val active = page(orgId, ClientListQuery(archived = false))
            assertEquals(1, active.rows.size)
            assertEquals("Активный", active.rows.single().name)

            val archived = page(orgId, ClientListQuery(archived = true))
            assertEquals(1, archived.rows.size)
            assertEquals("Архивный", archived.rows.single().name)
            assertTrue(archived.rows.single().archived)
        }

    @Test
    fun `сортировка по балансу учитывает последнюю запись журнала`() =
        runTest {
            val orgId = insertOrg()
            val performer = insertEmployee(orgId)
            val a = insertClient(orgId, "A")
            val b = insertClient(orgId, "B")
            // у A несколько записей: итоговый баланс должен браться по последней
            insertJournalEntry(orgId, a, performer, balanceAfter = 9_000, createdAt = baseInstant)
            insertJournalEntry(orgId, a, performer, balanceAfter = 1_000, createdAt = baseInstant + 1.seconds)
            insertJournalEntry(orgId, b, performer, balanceAfter = 5_000, createdAt = baseInstant)

            val asc = page(orgId, ClientListQuery(sortColumn = ClientSortColumn.BALANCE, ascending = true))
            assertEquals(listOf("A", "B"), asc.rows.map { it.name })

            val desc = page(orgId, ClientListQuery(sortColumn = ClientSortColumn.BALANCE, ascending = false))
            assertEquals(listOf("B", "A"), desc.rows.map { it.name })
        }

    @Test
    fun `сортировка по имени по убыванию`() =
        runTest {
            val orgId = insertOrg()
            insertClient(orgId, "Анна")
            insertClient(orgId, "Борис")
            insertClient(orgId, "Виктор")
            val result = page(orgId, ClientListQuery(sortColumn = ClientSortColumn.NAME, ascending = false))
            assertEquals(listOf("Виктор", "Борис", "Анна"), result.rows.map { it.name })
        }

    @Test
    fun `сортировка по дате рождения помещает null в конец`() =
        runTest {
            val orgId = insertOrg()
            insertClient(orgId, "Старший", birthday = LocalDate(1990, 1, 1))
            insertClient(orgId, "Младший", birthday = LocalDate(2000, 1, 1))
            insertClient(orgId, "Без даты", birthday = null)
            val result = page(orgId, ClientListQuery(sortColumn = ClientSortColumn.BIRTHDAY, ascending = true))
            assertEquals(listOf("Старший", "Младший", "Без даты"), result.rows.map { it.name })
        }

    @Test
    fun `пагинация возвращает срез и полный total`() =
        runTest {
            val orgId = insertOrg()
            listOf("A", "B", "C", "D", "E").forEach { insertClient(orgId, it) }

            val firstPage = page(orgId, ClientListQuery(sortColumn = ClientSortColumn.NAME, limit = 2, offset = 0))
            assertEquals(listOf("A", "B"), firstPage.rows.map { it.name })
            assertEquals(5, firstPage.total)

            val secondPage = page(orgId, ClientListQuery(sortColumn = ClientSortColumn.NAME, limit = 2, offset = 2))
            assertEquals(listOf("C", "D"), secondPage.rows.map { it.name })
            assertEquals(5, secondPage.total)
        }

    @Test
    fun `offset за концом выборки даёт пустую страницу с корректным total`() =
        runTest {
            val orgId = insertOrg()
            listOf("A", "B", "C").forEach { insertClient(orgId, it) }
            val result = page(orgId, ClientListQuery(limit = 10, offset = 100))
            assertTrue(result.rows.isEmpty())
            assertEquals(3, result.total)
        }

    @Test
    fun `поиск по имени регистронезависимый по подстроке`() =
        runTest {
            val orgId = insertOrg()
            insertClient(orgId, "Александр Иванов")
            insertClient(orgId, "Борис Петров")
            val result = page(orgId, ClientListQuery(search = "иван"))
            assertEquals(1, result.rows.size)
            assertEquals("Александр Иванов", result.rows.single().name)
        }

    @Test
    fun `поиск комбинируется с фильтром по полу`() =
        runTest {
            val orgId = insertOrg()
            insertClient(orgId, "Иван Мужчина", gender = "MALE")
            insertClient(orgId, "Иванна Женщина", gender = "FEMALE")
            val result = page(orgId, ClientListQuery(search = "иван", gender = Gender.FEMALE))
            assertEquals(1, result.rows.size)
            assertEquals("Иванна Женщина", result.rows.single().name)
        }

    @Test
    fun `баланс без журнала равен нулю`() =
        runTest {
            val orgId = insertOrg()
            insertClient(orgId, "Новичок")
            val result = page(orgId, ClientListQuery())
            assertEquals(Money.zero(Currency.RUB), result.rows.single().balance)
        }

    @Test
    fun `группы и контакты собираются по клиенту`() =
        runTest {
            val orgId = insertOrg()
            val a = insertClient(orgId, "A")
            val b = insertClient(orgId, "B")
            val group = insertGroup(orgId, "Самбо")
            addClientToGroup(a, group)
            insertContact(orgId, a, ContactType.PHONE.name, "+79991112233")
            insertContact(orgId, a, ContactType.EMAIL.name, "a@example.com")

            val result = page(orgId, ClientListQuery(sortColumn = ClientSortColumn.NAME))
            val rowA = result.rows.first { it.name == "A" }
            val rowB = result.rows.first { it.name == "B" }
            assertEquals(1, rowA.groups.size)
            assertEquals("Самбо", rowA.groups.single().name)
            assertEquals(2, rowA.contacts.size)
            assertTrue(rowA.contacts.any { it.type == ContactType.PHONE && it.value == "+79991112233" })
            assertTrue(rowB.groups.isEmpty())
            assertTrue(rowB.contacts.isEmpty())
        }

    @Test
    fun `фильтр birthday — находит клиента с ДР сегодня по MMDD`() =
        runTest {
            val orgId = insertOrg()
            insertClient(orgId, "Именинник", birthday = LocalDate(1990, 6, 19))
            insertClient(orgId, "Завтра", birthday = LocalDate(1990, 6, 20))
            insertClient(orgId, "Без ДР", birthday = null)
            val result = page(orgId, ClientListQuery(birthday = DateRange(LocalDate(2026, 6, 19), LocalDate(2026, 6, 19))))
            assertEquals(1, result.rows.size)
            assertEquals("Именинник", result.rows.single().name)
            assertEquals(1, result.total)
        }

    @Test
    fun `фильтр birthday this week — возвращает клиентов в диапазоне`() =
        runTest {
            val orgId = insertOrg()
            insertClient(orgId, "Начало недели", birthday = LocalDate(1990, 6, 19))
            insertClient(orgId, "Конец недели", birthday = LocalDate(1990, 6, 25))
            insertClient(orgId, "За пределами", birthday = LocalDate(1990, 6, 26))
            insertClient(orgId, "Без ДР", birthday = null)
            val result = page(orgId, ClientListQuery(birthday = DateRange(LocalDate(2026, 6, 19), LocalDate(2026, 6, 25))))
            assertEquals(2, result.rows.size)
            assertEquals(2, result.total)
            assertTrue(result.rows.any { it.name == "Начало недели" })
            assertTrue(result.rows.any { it.name == "Конец недели" })
        }

    @Test
    fun `фильтр birthday — клиент без ДР не попадает в выборку`() =
        runTest {
            val orgId = insertOrg()
            insertClient(orgId, "Без ДР", birthday = null)
            val result = page(orgId, ClientListQuery(birthday = DateRange(LocalDate(2026, 6, 19), LocalDate(2026, 6, 19))))
            assertEquals(0, result.rows.size)
            assertEquals(0, result.total)
        }

    @Test
    fun `фильтр birthday — переход через год (31 дек — 5 янв)`() =
        runTest {
            val orgId = insertOrg()
            insertClient(orgId, "31 декабря", birthday = LocalDate(1990, 12, 31))
            insertClient(orgId, "3 января", birthday = LocalDate(1990, 1, 3))
            insertClient(orgId, "15 июня", birthday = LocalDate(1990, 6, 15))
            val result = page(orgId, ClientListQuery(birthday = DateRange(LocalDate(2026, 12, 29), LocalDate(2026, 1, 5))))
            assertEquals(2, result.rows.size)
            assertTrue(result.rows.any { it.name == "31 декабря" })
            assertTrue(result.rows.any { it.name == "3 января" })
        }
}
