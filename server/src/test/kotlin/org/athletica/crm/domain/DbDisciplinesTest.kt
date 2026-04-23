package org.athletica.crm.domain

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.Lang
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.discipline.DbDisciplines
import org.athletica.crm.domain.discipline.Discipline
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

    private val ctx = RequestContext(Lang.EN, UserId.new(), orgId, employeeId, "user@example.com", "127.0.0.1", EmployeePermission())
    private val otherCtx = RequestContext(Lang.EN, UserId.new(), otherOrgId, otherEmployeeId, "user@example.com", "127.0.0.1", EmployeePermission())

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
            either<DomainError, Unit> {
                val list = TestPostgres.db.transaction { context(ctx, this) { disciplines.list() } }
                assertTrue(list.isEmpty())
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `list возвращает только дисциплины своей организации`() =
        runTest {
            either<DomainError, Unit> {
                TestPostgres.db.transaction { context(ctx, this) { disciplines.create(Discipline(DisciplineId.new(), "Теннис")) } }
                TestPostgres.db.transaction { context(otherCtx, this) { disciplines.create(Discipline(DisciplineId.new(), "Волейбол")) } }

                val list = TestPostgres.db.transaction { context(ctx, this) { disciplines.list() } }
                assertEquals(1, list.size)
                assertEquals("Теннис", list.first().name)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `list возвращает дисциплины отсортированные по имени`() =
        runTest {
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        disciplines.create(Discipline(DisciplineId.new(), "Хоккей"))
                        disciplines.create(Discipline(DisciplineId.new(), "Бокс"))
                        disciplines.create(Discipline(DisciplineId.new(), "Плавание"))
                    }
                }
                val list = TestPostgres.db.transaction { context(ctx, this) { disciplines.list() } }
                assertEquals(listOf("Бокс", "Плавание", "Хоккей"), list.map { it.name })
            }.getOrElse { fail("Unexpected error: $it") }
        }

    // ─── create ───────────────────────────────────────────────────────────────

    @Test
    fun `create добавляет дисциплину`() =
        runTest {
            val discipline = Discipline(DisciplineId.new(), "Футбол")
            either<DomainError, Unit> {
                TestPostgres.db.transaction { context(ctx, this) { disciplines.create(discipline) } }
                val list = TestPostgres.db.transaction { context(ctx, this) { disciplines.list() } }
                assertEquals(1, list.size)
                assertEquals(discipline.id, list.first().id)
                assertEquals("Футбол", list.first().name)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `create возвращает ошибку при дублировании имени в той же организации`() =
        runTest {
            either<DomainError, Unit> {
                TestPostgres.db.transaction { context(ctx, this) { disciplines.create(Discipline(DisciplineId.new(), "Хоккей")) } }
            }.getOrElse { fail("Setup failed: $it") }

            val result =
                either<DomainError, Unit> {
                    TestPostgres.db.transaction { context(ctx, this) { disciplines.create(Discipline(DisciplineId.new(), "Хоккей")) } }
                }
            assertEquals("DISCIPLINE_ALREADY_EXISTS", assertIs<Either.Left<DomainError>>(result).value.code)
        }

    @Test
    fun `create допускает одинаковое имя в разных организациях`() =
        runTest {
            either<DomainError, Unit> {
                TestPostgres.db.transaction { context(ctx, this) { disciplines.create(Discipline(DisciplineId.new(), "Баскетбол")) } }
                TestPostgres.db.transaction { context(otherCtx, this) { disciplines.create(Discipline(DisciplineId.new(), "Баскетбол")) } }
            }.getOrElse { fail("Unexpected error: $it") }
        }

    // ─── update ───────────────────────────────────────────────────────────────

    @Test
    fun `update изменяет название дисциплины`() =
        runTest {
            val id = DisciplineId.new()
            either<DomainError, Unit> {
                TestPostgres.db.transaction { context(ctx, this) { disciplines.create(Discipline(id, "Старое название")) } }
                TestPostgres.db.transaction { context(ctx, this) { disciplines.update(Discipline(id, "Новое название")) } }
                val list = TestPostgres.db.transaction { context(ctx, this) { disciplines.list() } }
                assertEquals("Новое название", list.first().name)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `update возвращает DISCIPLINE_NOT_FOUND для неизвестного id`() =
        runTest {
            val result =
                either<DomainError, Unit> {
                    TestPostgres.db.transaction { context(ctx, this) { disciplines.update(Discipline(DisciplineId.new(), "Что угодно")) } }
                }
            assertEquals("DISCIPLINE_NOT_FOUND", assertIs<Either.Left<DomainError>>(result).value.code)
        }

    @Test
    fun `update возвращает ошибку при конфликте имени с существующей дисциплиной`() =
        runTest {
            val id = DisciplineId.new()
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        disciplines.create(Discipline(id, "Бег"))
                        disciplines.create(Discipline(DisciplineId.new(), "Прыжки"))
                    }
                }
            }.getOrElse { fail("Setup failed: $it") }

            val result =
                either<DomainError, Unit> {
                    TestPostgres.db.transaction { context(ctx, this) { disciplines.update(Discipline(id, "Прыжки")) } }
                }
            assertEquals("DISCIPLINE_NAME_ALREADY_EXISTS", assertIs<Either.Left<DomainError>>(result).value.code)
        }

    @Test
    fun `update не затрагивает дисциплины другой организации`() =
        runTest {
            val id = DisciplineId.new()
            either<DomainError, Unit> {
                TestPostgres.db.transaction { context(ctx, this) { disciplines.create(Discipline(id, "Гимнастика")) } }
            }.getOrElse { fail("Setup failed: $it") }

            val updateResult =
                either<DomainError, Unit> {
                    TestPostgres.db.transaction { context(otherCtx, this) { disciplines.update(Discipline(id, "Другое")) } }
                }
            assertIs<Either.Left<DomainError>>(updateResult)

            either<DomainError, Unit> {
                val list = TestPostgres.db.transaction { context(ctx, this) { disciplines.list() } }
                assertEquals("Гимнастика", list.first().name)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    // ─── delete ───────────────────────────────────────────────────────────────

    @Test
    fun `delete удаляет дисциплину`() =
        runTest {
            val id = DisciplineId.new()
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        disciplines.create(Discipline(id, "Фехтование"))
                        disciplines.delete(listOf(id))
                    }
                }
                val list = TestPostgres.db.transaction { context(ctx, this) { disciplines.list() } }
                assertTrue(list.isEmpty())
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `delete с пустым списком не меняет данные`() =
        runTest {
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        disciplines.create(Discipline(DisciplineId.new(), "Самбо"))
                        disciplines.delete(emptyList())
                    }
                }
                val list = TestPostgres.db.transaction { context(ctx, this) { disciplines.list() } }
                assertEquals(1, list.size)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `delete удаляет несколько дисциплин за раз`() =
        runTest {
            val id1 = DisciplineId.new()
            val id2 = DisciplineId.new()
            val id3 = DisciplineId.new()
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        disciplines.create(Discipline(id1, "Дзюдо"))
                        disciplines.create(Discipline(id2, "Карате"))
                        disciplines.create(Discipline(id3, "Айкидо"))
                        disciplines.delete(listOf(id1, id2))
                    }
                }
                val list = TestPostgres.db.transaction { context(ctx, this) { disciplines.list() } }
                assertEquals(listOf("Айкидо"), list.map { it.name })
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `delete игнорирует id из другой организации`() =
        runTest {
            val id = DisciplineId.new()
            either<DomainError, Unit> {
                TestPostgres.db.transaction { context(ctx, this) { disciplines.create(Discipline(id, "Лыжи")) } }
                TestPostgres.db.transaction { context(otherCtx, this) { disciplines.delete(listOf(id)) } }
                val list = TestPostgres.db.transaction { context(ctx, this) { disciplines.list() } }
                assertEquals(1, list.size)
            }.getOrElse { fail("Unexpected error: $it") }
        }
}
