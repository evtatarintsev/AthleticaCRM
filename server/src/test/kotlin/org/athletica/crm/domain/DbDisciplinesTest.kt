package org.athletica.crm.domain

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestAuditLog
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.DisciplineId
import org.athletica.crm.core.Lang
import org.athletica.crm.core.OrgId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.UserId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.domain.audit.AuditActionType
import org.athletica.crm.domain.discipline.DbDisciplines
import org.athletica.crm.domain.discipline.Discipline
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DbDisciplinesTest {
    private val orgId = OrgId.new()
    private val otherOrgId = OrgId.new()

    private val ctx = RequestContext(Lang.EN, UserId.new(), orgId, "user@example.com", "127.0.0.1")
    private val otherCtx = RequestContext(Lang.EN, UserId.new(), otherOrgId, "user@example.com", "127.0.0.1")

    private lateinit var audit: TestAuditLog
    private lateinit var disciplines: DbDisciplines

    @Before
    fun setUp() {
        TestPostgres.truncate()
        audit = TestAuditLog()
        disciplines = DbDisciplines(TestPostgres.db, audit)
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
            context(ctx) {
                val result = disciplines.list()
                assertTrue(assertIs<Either.Right<List<Discipline>>>(result).value.isEmpty())
            }
        }

    @Test
    fun `list возвращает только дисциплины своей организации`() =
        runTest {
            context(ctx) { disciplines.create(Discipline(DisciplineId.new(), "Теннис")) }
            context(otherCtx) { disciplines.create(Discipline(DisciplineId.new(), "Волейбол")) }

            context(ctx) {
                val list = assertIs<Either.Right<List<Discipline>>>(disciplines.list()).value
                assertEquals(1, list.size)
                assertEquals("Теннис", list.first().name)
            }
        }

    @Test
    fun `list возвращает дисциплины отсортированные по имени`() =
        runTest {
            context(ctx) {
                disciplines.create(Discipline(DisciplineId.new(), "Хоккей"))
                disciplines.create(Discipline(DisciplineId.new(), "Бокс"))
                disciplines.create(Discipline(DisciplineId.new(), "Плавание"))

                val list = assertIs<Either.Right<List<Discipline>>>(disciplines.list()).value
                assertEquals(listOf("Бокс", "Плавание", "Хоккей"), list.map { it.name })
            }
        }

    // ─── create ───────────────────────────────────────────────────────────────

    @Test
    fun `create добавляет дисциплину`() =
        runTest {
            val discipline = Discipline(DisciplineId.new(), "Футбол")
            context(ctx) {
                assertIs<Either.Right<Unit>>(disciplines.create(discipline))

                val list = assertIs<Either.Right<List<Discipline>>>(disciplines.list()).value
                assertEquals(1, list.size)
                assertEquals(discipline.id, list.first().id)
                assertEquals("Футбол", list.first().name)
            }
        }

    @Test
    fun `create возвращает ошибку при дублировании имени в той же организации`() =
        runTest {
            context(ctx) {
                disciplines.create(Discipline(DisciplineId.new(), "Хоккей"))
                val result = disciplines.create(Discipline(DisciplineId.new(), "Хоккей"))
                assertEquals("DISCIPLINE_ALREADY_EXISTS", assertIs<Either.Left<CommonDomainError>>(result).value.code)
            }
        }

    @Test
    fun `create допускает одинаковое имя в разных организациях`() =
        runTest {
            context(ctx) {
                assertIs<Either.Right<Unit>>(disciplines.create(Discipline(DisciplineId.new(), "Баскетбол")))
            }
            context(otherCtx) {
                assertIs<Either.Right<Unit>>(disciplines.create(Discipline(DisciplineId.new(), "Баскетбол")))
            }
        }

    @Test
    fun `create пишет событие в audit log`() =
        runTest {
            val discipline = Discipline(DisciplineId.new(), "Бег")
            context(ctx) { disciplines.create(discipline) }

            val event = audit.channel.tryReceive().getOrNull()!!
            assertEquals(AuditActionType.CREATE, event.actionType)
            assertEquals("discipline", event.entityType)
            assertEquals(discipline.id.value, event.entityId)
        }

    // ─── update ───────────────────────────────────────────────────────────────

    @Test
    fun `update изменяет название дисциплины`() =
        runTest {
            val id = DisciplineId.new()
            context(ctx) {
                disciplines.create(Discipline(id, "Старое название"))
                assertIs<Either.Right<Unit>>(disciplines.update(Discipline(id, "Новое название")))

                val list = assertIs<Either.Right<List<Discipline>>>(disciplines.list()).value
                assertEquals("Новое название", list.first().name)
            }
        }

    @Test
    fun `update возвращает DISCIPLINE_NOT_FOUND для неизвестного id`() =
        runTest {
            context(ctx) {
                val result = disciplines.update(Discipline(DisciplineId.new(), "Что угодно"))
                assertEquals("DISCIPLINE_NOT_FOUND", assertIs<Either.Left<CommonDomainError>>(result).value.code)
            }
        }

    @Test
    fun `update возвращает ошибку при конфликте имени с существующей дисциплиной`() =
        runTest {
            val id = DisciplineId.new()
            context(ctx) {
                disciplines.create(Discipline(id, "Бег"))
                disciplines.create(Discipline(DisciplineId.new(), "Прыжки"))

                val result = disciplines.update(Discipline(id, "Прыжки"))
                assertEquals(
                    "DISCIPLINE_NAME_ALREADY_EXISTS",
                    assertIs<Either.Left<CommonDomainError>>(result).value.code,
                )
            }
        }

    @Test
    fun `update не затрагивает дисциплины другой организации`() =
        runTest {
            val id = DisciplineId.new()
            context(ctx) { disciplines.create(Discipline(id, "Гимнастика")) }

            context(otherCtx) {
                val result = disciplines.update(Discipline(id, "Другое"))
                assertIs<Either.Left<CommonDomainError>>(result)
            }

            context(ctx) {
                val list = assertIs<Either.Right<List<Discipline>>>(disciplines.list()).value
                assertEquals("Гимнастика", list.first().name)
            }
        }

    @Test
    fun `update пишет событие в audit log`() =
        runTest {
            val id = DisciplineId.new()
            context(ctx) {
                disciplines.create(Discipline(id, "Старое"))
                audit.channel.tryReceive() // сбрасываем событие create
                disciplines.update(Discipline(id, "Новое"))
            }

            val event = audit.channel.tryReceive().getOrNull()!!
            assertEquals(AuditActionType.UPDATE, event.actionType)
            assertEquals("discipline", event.entityType)
            assertEquals(id.value, event.entityId)
        }

    // ─── delete ───────────────────────────────────────────────────────────────

    @Test
    fun `delete удаляет дисциплину`() =
        runTest {
            val id = DisciplineId.new()
            context(ctx) {
                disciplines.create(Discipline(id, "Фехтование"))
                assertIs<Either.Right<Unit>>(disciplines.delete(listOf(id)))

                val list = assertIs<Either.Right<List<Discipline>>>(disciplines.list()).value
                assertTrue(list.isEmpty())
            }
        }

    @Test
    fun `delete с пустым списком не меняет данные`() =
        runTest {
            context(ctx) {
                disciplines.create(Discipline(DisciplineId.new(), "Самбо"))
                assertIs<Either.Right<Unit>>(disciplines.delete(emptyList()))

                val list = assertIs<Either.Right<List<Discipline>>>(disciplines.list()).value
                assertEquals(1, list.size)
            }
        }

    @Test
    fun `delete удаляет несколько дисциплин за раз`() =
        runTest {
            val id1 = DisciplineId.new()
            val id2 = DisciplineId.new()
            val id3 = DisciplineId.new()
            context(ctx) {
                disciplines.create(Discipline(id1, "Дзюдо"))
                disciplines.create(Discipline(id2, "Карате"))
                disciplines.create(Discipline(id3, "Айкидо"))

                disciplines.delete(listOf(id1, id2))

                val list = assertIs<Either.Right<List<Discipline>>>(disciplines.list()).value
                assertEquals(listOf("Айкидо"), list.map { it.name })
            }
        }

    @Test
    fun `delete игнорирует id из другой организации`() =
        runTest {
            val id = DisciplineId.new()
            context(ctx) { disciplines.create(Discipline(id, "Лыжи")) }

            context(otherCtx) {
                assertIs<Either.Right<Unit>>(disciplines.delete(listOf(id)))
            }

            context(ctx) {
                val list = assertIs<Either.Right<List<Discipline>>>(disciplines.list()).value
                assertEquals(1, list.size)
            }
        }

    @Test
    fun `delete пишет событие в audit log для каждой удалённой дисциплины`() =
        runTest {
            val id1 = DisciplineId.new()
            val id2 = DisciplineId.new()
            context(ctx) {
                disciplines.create(Discipline(id1, "Борьба"))
                disciplines.create(Discipline(id2, "Тхэквондо"))
                // сбрасываем два события create
                audit.channel.tryReceive()
                audit.channel.tryReceive()

                disciplines.delete(listOf(id1, id2))
            }

            val events =
                buildList {
                    repeat(2) { audit.channel.tryReceive().getOrNull()?.let(::add) }
                }
            assertEquals(2, events.size)
            assertTrue(events.all { it.actionType == AuditActionType.DELETE })
            assertTrue(events.all { it.entityType == "discipline" })
        }
}
