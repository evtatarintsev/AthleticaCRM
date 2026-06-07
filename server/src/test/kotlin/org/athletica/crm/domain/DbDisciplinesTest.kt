package org.athletica.crm.domain

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.discipline.DbDisciplines
import org.athletica.crm.domain.employees.EmployeePermission
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class DbDisciplinesTest {
    private val orgId = OrgId.new()
    private val otherOrgId = OrgId.new()
    private val employeeId = EmployeeId.new()
    private val otherEmployeeId = EmployeeId.new()

    private val ctx = EmployeeRequestContext(lang = Lang.EN, orgId = orgId, currency = Currency.RUB, userId = UserId.new(), branchId = BranchId.new(), employeeId = employeeId, username = "user@example.com", clientIp = "127.0.0.1", permission = EmployeePermission())
    private val otherCtx = EmployeeRequestContext(lang = Lang.EN, orgId = otherOrgId, currency = Currency.RUB, userId = UserId.new(), branchId = BranchId.new(), employeeId = otherEmployeeId, username = "user@example.com", clientIp = "127.0.0.1", permission = EmployeePermission())

    private lateinit var disciplines: DbDisciplines

    @Before
    fun setUp() {
        TestPostgres.truncate()
        disciplines = DbDisciplines()
        runBlocking {
            TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .bind("id", orgId).bind("name", "Org 1").execute()
            TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .bind("id", otherOrgId).bind("name", "Org 2").execute()
        }
    }

    // ─── list ─────────────────────────────────────────────────────────────────

    @Test
    fun `list возвращает пустой список если дисциплин нет`() =
        runTest {
            either {
                val list = TestPostgres.db.transaction { context(ctx) { disciplines.list() } }
                assertTrue(list.isEmpty())
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `list возвращает только дисциплины своей организации`() =
        runTest {
            either {
                TestPostgres.db.transaction { context(ctx) { disciplines.new(DisciplineId.new(), "Теннис").save() } }
                TestPostgres.db.transaction { context(otherCtx) { disciplines.new(DisciplineId.new(), "Волейбол").save() } }

                val list = TestPostgres.db.transaction { context(ctx) { disciplines.list() } }
                assertEquals(1, list.size)
                assertEquals("Теннис", list.first().name)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `list возвращает дисциплины отсортированные по имени`() =
        runTest {
            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        disciplines.new(DisciplineId.new(), "Хоккей").save()
                        disciplines.new(DisciplineId.new(), "Бокс").save()
                        disciplines.new(DisciplineId.new(), "Плавание").save()
                    }
                }
                val list = TestPostgres.db.transaction { context(ctx) { disciplines.list() } }
                assertEquals(listOf("Бокс", "Плавание", "Хоккей"), list.map { it.name })
            }.getOrElse { fail("Unexpected error: $it") }
        }

    // ─── new + save ─────────────────────────────────────────────────────────────

    @Test
    fun `save добавляет новую дисциплину`() =
        runTest {
            val id = DisciplineId.new()
            either {
                TestPostgres.db.transaction { context(ctx) { disciplines.new(id, "Футбол").save() } }
                val list = TestPostgres.db.transaction { context(ctx) { disciplines.list() } }
                assertEquals(1, list.size)
                assertEquals(id, list.first().id)
                assertEquals("Футбол", list.first().name)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `save возвращает ошибку при дублировании имени в той же организации`() =
        runTest {
            either {
                TestPostgres.db.transaction { context(ctx) { disciplines.new(DisciplineId.new(), "Хоккей").save() } }
            }.getOrElse { fail("Setup failed: $it") }

            val result =
                either {
                    TestPostgres.db.transaction { context(ctx) { disciplines.new(DisciplineId.new(), "Хоккей").save() } }
                }
            assertEquals("DISCIPLINE_ALREADY_EXISTS", assertIs<Either.Left<DomainError>>(result).value.code)
        }

    @Test
    fun `save допускает одинаковое имя в разных организациях`() =
        runTest {
            either {
                TestPostgres.db.transaction { context(ctx) { disciplines.new(DisciplineId.new(), "Баскетбол").save() } }
                TestPostgres.db.transaction { context(otherCtx) { disciplines.new(DisciplineId.new(), "Баскетбол").save() } }
            }.getOrElse { fail("Unexpected error: $it") }
        }

    // ─── byId + withNew + save ─────────────────────────────────────────────────

    @Test
    fun `withNew + save изменяет название дисциплины`() =
        runTest {
            val id = DisciplineId.new()
            either {
                TestPostgres.db.transaction { context(ctx) { disciplines.new(id, "Старое название").save() } }
                TestPostgres.db.transaction { context(ctx) { disciplines.byId(id).withNew("Новое название").save() } }
                val list = TestPostgres.db.transaction { context(ctx) { disciplines.list() } }
                assertEquals("Новое название", list.first().name)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `byId возвращает DISCIPLINE_NOT_FOUND для неизвестного id`() =
        runTest {
            val result =
                either {
                    TestPostgres.db.transaction { context(ctx) { disciplines.byId(DisciplineId.new()).withNew("Что угодно").save() } }
                }
            assertEquals("DISCIPLINE_NOT_FOUND", assertIs<Either.Left<DomainError>>(result).value.code)
        }

    @Test
    fun `save возвращает ошибку при конфликте имени с существующей дисциплиной`() =
        runTest {
            val id = DisciplineId.new()
            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        disciplines.new(id, "Бег").save()
                        disciplines.new(DisciplineId.new(), "Прыжки").save()
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val result =
                either {
                    TestPostgres.db.transaction { context(ctx) { disciplines.byId(id).withNew("Прыжки").save() } }
                }
            assertEquals("DISCIPLINE_ALREADY_EXISTS", assertIs<Either.Left<DomainError>>(result).value.code)
        }

    @Test
    fun `byId не находит дисциплины другой организации`() =
        runTest {
            val id = DisciplineId.new()
            either {
                TestPostgres.db.transaction { context(ctx) { disciplines.new(id, "Гимнастика").save() } }
            }.getOrElse { fail("Setup failed: $it") }

            val updateResult =
                either {
                    TestPostgres.db.transaction { context(otherCtx) { disciplines.byId(id).withNew("Другое").save() } }
                }
            assertIs<Either.Left<DomainError>>(updateResult)

            either {
                val list = TestPostgres.db.transaction { context(ctx) { disciplines.list() } }
                assertEquals("Гимнастика", list.first().name)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    // ─── delete ───────────────────────────────────────────────────────────────

    @Test
    fun `delete удаляет дисциплину`() =
        runTest {
            val id = DisciplineId.new()
            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        disciplines.new(id, "Фехтование").save()
                        disciplines.byIds(listOf(id)).forEach { it.delete() }
                    }
                }
                val list = TestPostgres.db.transaction { context(ctx) { disciplines.list() } }
                assertTrue(list.isEmpty())
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `delete с пустым списком не меняет данные`() =
        runTest {
            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        disciplines.new(DisciplineId.new(), "Самбо").save()
                        disciplines.byIds(emptyList()).forEach { it.delete() }
                    }
                }
                val list = TestPostgres.db.transaction { context(ctx) { disciplines.list() } }
                assertEquals(1, list.size)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `delete удаляет несколько дисциплин за раз`() =
        runTest {
            val id1 = DisciplineId.new()
            val id2 = DisciplineId.new()
            val id3 = DisciplineId.new()
            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        disciplines.new(id1, "Дзюдо").save()
                        disciplines.new(id2, "Карате").save()
                        disciplines.new(id3, "Айкидо").save()
                        disciplines.byIds(listOf(id1, id2)).forEach { it.delete() }
                    }
                }
                val list = TestPostgres.db.transaction { context(ctx) { disciplines.list() } }
                assertEquals(listOf("Айкидо"), list.map { it.name })
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `delete не трогает дисциплину из другой организации`() =
        runTest {
            val id = DisciplineId.new()
            either {
                TestPostgres.db.transaction { context(ctx) { disciplines.new(id, "Лыжи").save() } }
            }.getOrElse { fail("Setup failed: $it") }

            val deleteResult =
                either {
                    TestPostgres.db.transaction { context(otherCtx) { disciplines.byIds(listOf(id)).forEach { it.delete() } } }
                }
            assertIs<Either.Left<DomainError>>(deleteResult)

            either {
                val list = TestPostgres.db.transaction { context(ctx) { disciplines.list() } }
                assertEquals(1, list.size)
            }.getOrElse { fail("Unexpected error: $it") }
        }
}
