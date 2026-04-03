package org.athletica.crm.usecases

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestAuditLog
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.sports.CreateSportRequest
import org.athletica.crm.api.schemas.sports.DeleteSportRequest
import org.athletica.crm.api.schemas.sports.SportDetailResponse
import org.athletica.crm.api.schemas.sports.UpdateSportRequest
import org.athletica.crm.core.Lang
import org.athletica.crm.core.OrgId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.UserId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.usecases.sports.createSport
import org.athletica.crm.usecases.sports.deleteSport
import org.athletica.crm.usecases.sports.sportList
import org.athletica.crm.usecases.sports.updateSport
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class SportsUsecasesTest {
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

    // ─── createSport ───────────────────────────────────────────────────────────

    @Test
    fun `createSport returns SportDetailResponse on success`() =
        runTest {
            val request = CreateSportRequest(id = Uuid.generateV7(), name = "Футбол")
            context(TestPostgres.db, ctx, TestAuditLog()) {
                val result = createSport(request)
                val sport = assertIs<Either.Right<SportDetailResponse>>(result).value
                assertEquals(request.id, sport.id)
                assertEquals("Футбол", sport.name)
            }
        }

    @Test
    fun `createSport returns error when name already exists in same org`() =
        runTest {
            context(TestPostgres.db, ctx, TestAuditLog()) {
                createSport(CreateSportRequest(id = Uuid.generateV7(), name = "Хоккей"))
                val result = createSport(CreateSportRequest(id = Uuid.generateV7(), name = "Хоккей"))
                val error = assertIs<Either.Left<CommonDomainError>>(result).value
                assertEquals("SPORT_ALREADY_EXISTS", error.code)
            }
        }

    @Test
    fun `createSport allows same name in different organizations`() =
        runTest {
            context(TestPostgres.db, ctx, TestAuditLog()) {
                assertIs<Either.Right<SportDetailResponse>>(
                    createSport(CreateSportRequest(id = Uuid.generateV7(), name = "Баскетбол")),
                )
            }
            context(TestPostgres.db, otherCtx, TestAuditLog()) {
                assertIs<Either.Right<SportDetailResponse>>(
                    createSport(CreateSportRequest(id = Uuid.generateV7(), name = "Баскетбол")),
                )
            }
        }

    // ─── sportList ─────────────────────────────────────────────────────────────

    @Test
    fun `sportList returns empty list when no sports exist`() =
        runTest {
            context(TestPostgres.db, ctx) {
                val result = sportList()
                val list = assertIs<Either.Right<List<SportDetailResponse>>>(result).value
                assertTrue(list.isEmpty())
            }
        }

    @Test
    fun `sportList returns only sports of current org`() =
        runTest {
            context(TestPostgres.db, ctx, TestAuditLog()) {
                createSport(CreateSportRequest(id = Uuid.generateV7(), name = "Теннис"))
            }
            context(TestPostgres.db, otherCtx, TestAuditLog()) {
                createSport(CreateSportRequest(id = Uuid.generateV7(), name = "Волейбол"))
            }
            context(TestPostgres.db, ctx) {
                val list = assertIs<Either.Right<List<SportDetailResponse>>>(sportList()).value
                assertEquals(1, list.size)
                assertEquals("Теннис", list.first().name)
            }
        }

    @Test
    fun `sportList returns sports sorted by name`() =
        runTest {
            context(TestPostgres.db, ctx, TestAuditLog()) {
                createSport(CreateSportRequest(id = Uuid.generateV7(), name = "Хоккей"))
                createSport(CreateSportRequest(id = Uuid.generateV7(), name = "Бокс"))
                createSport(CreateSportRequest(id = Uuid.generateV7(), name = "Плавание"))

                val list = assertIs<Either.Right<List<SportDetailResponse>>>(sportList()).value
                assertEquals(listOf("Бокс", "Плавание", "Хоккей"), list.map { it.name })
            }
        }

    // ─── updateSport ───────────────────────────────────────────────────────────

    @Test
    fun `updateSport returns updated sport on success`() =
        runTest {
            context(TestPostgres.db, ctx, TestAuditLog()) {
                val id = Uuid.generateV7()
                createSport(CreateSportRequest(id = id, name = "Старое название"))

                val result = updateSport(UpdateSportRequest(id = id, name = "Новое название"))
                val sport = assertIs<Either.Right<SportDetailResponse>>(result).value
                assertEquals(id, sport.id)
                assertEquals("Новое название", sport.name)
            }
        }

    @Test
    fun `updateSport returns SPORT_NOT_FOUND for unknown id`() =
        runTest {
            context(TestPostgres.db, ctx, TestAuditLog()) {
                val result = updateSport(UpdateSportRequest(id = Uuid.generateV7(), name = "Что угодно"))
                val error = assertIs<Either.Left<CommonDomainError>>(result).value
                assertEquals("SPORT_NOT_FOUND", error.code)
            }
        }

    @Test
    fun `updateSport returns error when new name conflicts with existing sport`() =
        runTest {
            context(TestPostgres.db, ctx, TestAuditLog()) {
                val id = Uuid.generateV7()
                createSport(CreateSportRequest(id = id, name = "Бег"))
                createSport(CreateSportRequest(id = Uuid.generateV7(), name = "Прыжки"))

                val result = updateSport(UpdateSportRequest(id = id, name = "Прыжки"))
                val error = assertIs<Either.Left<CommonDomainError>>(result).value
                assertEquals("SPORT_NAME_ALREADY_EXISTS", error.code)
            }
        }

    @Test
    fun `updateSport does not affect other org`() =
        runTest {
            val sharedName = "Гимнастика"
            val id = Uuid.generateV7()
            context(TestPostgres.db, ctx, TestAuditLog()) {
                createSport(CreateSportRequest(id = id, name = sharedName))
            }
            // попытка обновить спорт чужой орги — 0 строк → SPORT_NOT_FOUND
            context(TestPostgres.db, otherCtx, TestAuditLog()) {
                val result = updateSport(UpdateSportRequest(id = id, name = "Другое"))
                assertIs<Either.Left<CommonDomainError>>(result)
            }
            // оригинальное название не изменилось
            context(TestPostgres.db, ctx) {
                val list = assertIs<Either.Right<List<SportDetailResponse>>>(sportList()).value
                assertEquals(sharedName, list.first().name)
            }
        }

    // ─── deleteSport ───────────────────────────────────────────────────────────

    @Test
    fun `deleteSport removes sport from list`() =
        runTest {
            context(TestPostgres.db, ctx, TestAuditLog()) {
                val id = Uuid.generateV7()
                createSport(CreateSportRequest(id = id, name = "Фехтование"))

                assertIs<Either.Right<Unit>>(deleteSport(DeleteSportRequest(ids = listOf(id))))

                val list = assertIs<Either.Right<List<SportDetailResponse>>>(sportList()).value
                assertTrue(list.isEmpty())
            }
        }

    @Test
    fun `deleteSport with empty list is a no-op`() =
        runTest {
            context(TestPostgres.db, ctx, TestAuditLog()) {
                createSport(CreateSportRequest(id = Uuid.generateV7(), name = "Самбо"))

                assertIs<Either.Right<Unit>>(deleteSport(DeleteSportRequest(ids = emptyList())))

                val list = assertIs<Either.Right<List<SportDetailResponse>>>(sportList()).value
                assertEquals(1, list.size)
            }
        }

    @Test
    fun `deleteSport removes multiple sports atomically`() =
        runTest {
            context(TestPostgres.db, ctx, TestAuditLog()) {
                val id1 = Uuid.generateV7()
                val id2 = Uuid.generateV7()
                val id3 = Uuid.generateV7()
                createSport(CreateSportRequest(id = id1, name = "Дзюдо"))
                createSport(CreateSportRequest(id = id2, name = "Карате"))
                createSport(CreateSportRequest(id = id3, name = "Айкидо"))

                deleteSport(DeleteSportRequest(ids = listOf(id1, id2)))

                val list = assertIs<Either.Right<List<SportDetailResponse>>>(sportList()).value
                assertEquals(listOf("Айкидо"), list.map { it.name })
            }
        }

    @Test
    fun `deleteSport ignores ids from other org`() =
        runTest {
            val id = Uuid.generateV7()
            context(TestPostgres.db, ctx, TestAuditLog()) {
                createSport(CreateSportRequest(id = id, name = "Лыжи"))
            }
            // удаляем из чужой орги — должно молча игнорироваться
            context(TestPostgres.db, otherCtx) {
                assertIs<Either.Right<Unit>>(deleteSport(DeleteSportRequest(ids = listOf(id))))
            }
            // спорт должен остаться
            context(TestPostgres.db, ctx) {
                val list = assertIs<Either.Right<List<SportDetailResponse>>>(sportList()).value
                assertEquals(1, list.size)
            }
        }
}
