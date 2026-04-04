package org.athletica.crm.usecases

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestAuditLog
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.disciplines.CreateDisciplineRequest
import org.athletica.crm.api.schemas.disciplines.DeleteDisciplineRequest
import org.athletica.crm.api.schemas.disciplines.DisciplineDetailResponse
import org.athletica.crm.api.schemas.disciplines.UpdateDisciplineRequest
import org.athletica.crm.core.Lang
import org.athletica.crm.core.OrgId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.UserId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.usecases.disciplines.createDiscipline
import org.athletica.crm.usecases.disciplines.deleteDiscipline
import org.athletica.crm.usecases.disciplines.disciplineList
import org.athletica.crm.usecases.disciplines.updateDiscipline
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class DisciplinesUsecasesTest {
    private val orgId = OrgId.new()
    private val otherOrgId = OrgId.new()
    private val userId = UserId.new()

    private val ctx = RequestContext(Lang.EN, userId, orgId, "", null)
    private val otherCtx = RequestContext(Lang.EN, userId, otherOrgId, "", null)

    @Before
    fun setUp() =
        TestPostgres.truncate().also {
            runBlocking {
                TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                    .bind("id", orgId).bind("name", "Org 1").execute()
                TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                    .bind("id", otherOrgId).bind("name", "Org 2").execute()
            }
        }

    // ─── createDiscipline ──────────────────────────────────────────────────────

    @Test
    fun `createDiscipline returns DisciplineDetailResponse on success`() =
        runTest {
            val request = CreateDisciplineRequest(id = Uuid.generateV7(), name = "Футбол")
            context(TestPostgres.db, ctx, TestAuditLog()) {
                val result = createDiscipline(request)
                val discipline = assertIs<Either.Right<DisciplineDetailResponse>>(result).value
                assertEquals(request.id, discipline.id)
                assertEquals("Футбол", discipline.name)
            }
        }

    @Test
    fun `createDiscipline returns error when name already exists in same org`() =
        runTest {
            context(TestPostgres.db, ctx, TestAuditLog()) {
                createDiscipline(CreateDisciplineRequest(id = Uuid.generateV7(), name = "Хоккей"))
                val result = createDiscipline(CreateDisciplineRequest(id = Uuid.generateV7(), name = "Хоккей"))
                val error = assertIs<Either.Left<CommonDomainError>>(result).value
                assertEquals("DISCIPLINE_ALREADY_EXISTS", error.code)
            }
        }

    @Test
    fun `createDiscipline allows same name in different organizations`() =
        runTest {
            context(TestPostgres.db, ctx, TestAuditLog()) {
                assertIs<Either.Right<DisciplineDetailResponse>>(
                    createDiscipline(CreateDisciplineRequest(id = Uuid.generateV7(), name = "Баскетбол")),
                )
            }
            context(TestPostgres.db, otherCtx, TestAuditLog()) {
                assertIs<Either.Right<DisciplineDetailResponse>>(
                    createDiscipline(CreateDisciplineRequest(id = Uuid.generateV7(), name = "Баскетбол")),
                )
            }
        }

    // ─── disciplineList ────────────────────────────────────────────────────────

    @Test
    fun `disciplineList returns empty list when no disciplines exist`() =
        runTest {
            context(TestPostgres.db, ctx) {
                val result = disciplineList()
                val list = assertIs<Either.Right<List<DisciplineDetailResponse>>>(result).value
                assertTrue(list.isEmpty())
            }
        }

    @Test
    fun `disciplineList returns only disciplines of current org`() =
        runTest {
            context(TestPostgres.db, ctx, TestAuditLog()) {
                createDiscipline(CreateDisciplineRequest(id = Uuid.generateV7(), name = "Теннис"))
            }
            context(TestPostgres.db, otherCtx, TestAuditLog()) {
                createDiscipline(CreateDisciplineRequest(id = Uuid.generateV7(), name = "Волейбол"))
            }
            context(TestPostgres.db, ctx) {
                val list = assertIs<Either.Right<List<DisciplineDetailResponse>>>(disciplineList()).value
                assertEquals(1, list.size)
                assertEquals("Теннис", list.first().name)
            }
        }

    @Test
    fun `disciplineList returns disciplines sorted by name`() =
        runTest {
            context(TestPostgres.db, ctx, TestAuditLog()) {
                createDiscipline(CreateDisciplineRequest(id = Uuid.generateV7(), name = "Хоккей"))
                createDiscipline(CreateDisciplineRequest(id = Uuid.generateV7(), name = "Бокс"))
                createDiscipline(CreateDisciplineRequest(id = Uuid.generateV7(), name = "Плавание"))

                val list = assertIs<Either.Right<List<DisciplineDetailResponse>>>(disciplineList()).value
                assertEquals(listOf("Бокс", "Плавание", "Хоккей"), list.map { it.name })
            }
        }

    // ─── updateDiscipline ──────────────────────────────────────────────────────

    @Test
    fun `updateDiscipline returns updated discipline on success`() =
        runTest {
            context(TestPostgres.db, ctx, TestAuditLog()) {
                val id = Uuid.generateV7()
                createDiscipline(CreateDisciplineRequest(id = id, name = "Старое название"))

                val result = updateDiscipline(UpdateDisciplineRequest(id = id, name = "Новое название"))
                val discipline = assertIs<Either.Right<DisciplineDetailResponse>>(result).value
                assertEquals(id, discipline.id)
                assertEquals("Новое название", discipline.name)
            }
        }

    @Test
    fun `updateDiscipline returns DISCIPLINE_NOT_FOUND for unknown id`() =
        runTest {
            context(TestPostgres.db, ctx, TestAuditLog()) {
                val result = updateDiscipline(UpdateDisciplineRequest(id = Uuid.generateV7(), name = "Что угодно"))
                val error = assertIs<Either.Left<CommonDomainError>>(result).value
                assertEquals("DISCIPLINE_NOT_FOUND", error.code)
            }
        }

    @Test
    fun `updateDiscipline returns error when new name conflicts with existing discipline`() =
        runTest {
            context(TestPostgres.db, ctx, TestAuditLog()) {
                val id = Uuid.generateV7()
                createDiscipline(CreateDisciplineRequest(id = id, name = "Бег"))
                createDiscipline(CreateDisciplineRequest(id = Uuid.generateV7(), name = "Прыжки"))

                val result = updateDiscipline(UpdateDisciplineRequest(id = id, name = "Прыжки"))
                val error = assertIs<Either.Left<CommonDomainError>>(result).value
                assertEquals("DISCIPLINE_NAME_ALREADY_EXISTS", error.code)
            }
        }

    @Test
    fun `updateDiscipline does not affect other org`() =
        runTest {
            val sharedName = "Гимнастика"
            val id = Uuid.generateV7()
            context(TestPostgres.db, ctx, TestAuditLog()) {
                createDiscipline(CreateDisciplineRequest(id = id, name = sharedName))
            }
            // попытка обновить дисциплину чужой орги — 0 строк → DISCIPLINE_NOT_FOUND
            context(TestPostgres.db, otherCtx, TestAuditLog()) {
                val result = updateDiscipline(UpdateDisciplineRequest(id = id, name = "Другое"))
                assertIs<Either.Left<CommonDomainError>>(result)
            }
            // оригинальное название не изменилось
            context(TestPostgres.db, ctx) {
                val list = assertIs<Either.Right<List<DisciplineDetailResponse>>>(disciplineList()).value
                assertEquals(sharedName, list.first().name)
            }
        }

    // ─── deleteDiscipline ──────────────────────────────────────────────────────

    @Test
    fun `deleteDiscipline removes discipline from list`() =
        runTest {
            context(TestPostgres.db, ctx, TestAuditLog()) {
                val id = Uuid.generateV7()
                createDiscipline(CreateDisciplineRequest(id = id, name = "Фехтование"))

                assertIs<Either.Right<Unit>>(deleteDiscipline(DeleteDisciplineRequest(ids = listOf(id))))

                val list = assertIs<Either.Right<List<DisciplineDetailResponse>>>(disciplineList()).value
                assertTrue(list.isEmpty())
            }
        }

    @Test
    fun `deleteDiscipline with empty list is a no-op`() =
        runTest {
            context(TestPostgres.db, ctx, TestAuditLog()) {
                createDiscipline(CreateDisciplineRequest(id = Uuid.generateV7(), name = "Самбо"))

                assertIs<Either.Right<Unit>>(deleteDiscipline(DeleteDisciplineRequest(ids = emptyList())))

                val list = assertIs<Either.Right<List<DisciplineDetailResponse>>>(disciplineList()).value
                assertEquals(1, list.size)
            }
        }

    @Test
    fun `deleteDiscipline removes multiple disciplines atomically`() =
        runTest {
            context(TestPostgres.db, ctx, TestAuditLog()) {
                val id1 = Uuid.generateV7()
                val id2 = Uuid.generateV7()
                val id3 = Uuid.generateV7()
                createDiscipline(CreateDisciplineRequest(id = id1, name = "Дзюдо"))
                createDiscipline(CreateDisciplineRequest(id = id2, name = "Карате"))
                createDiscipline(CreateDisciplineRequest(id = id3, name = "Айкидо"))

                deleteDiscipline(DeleteDisciplineRequest(ids = listOf(id1, id2)))

                val list = assertIs<Either.Right<List<DisciplineDetailResponse>>>(disciplineList()).value
                assertEquals(listOf("Айкидо"), list.map { it.name })
            }
        }

    @Test
    fun `deleteDiscipline ignores ids from other org`() =
        runTest {
            val id = Uuid.generateV7()
            context(TestPostgres.db, ctx, TestAuditLog()) {
                createDiscipline(CreateDisciplineRequest(id = id, name = "Лыжи"))
            }
            // удаляем из чужой орги — должно молча игнорироваться
            context(TestPostgres.db, otherCtx,TestAuditLog()) {
                assertIs<Either.Right<Unit>>(deleteDiscipline(DeleteDisciplineRequest(ids = listOf(id))))
            }
            // дисциплина должна остаться
            context(TestPostgres.db, ctx) {
                val list = assertIs<Either.Right<List<DisciplineDetailResponse>>>(disciplineList()).value
                assertEquals(1, list.size)
            }
        }
}
