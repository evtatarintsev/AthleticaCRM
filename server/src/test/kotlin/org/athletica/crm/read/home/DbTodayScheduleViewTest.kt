package org.athletica.crm.read.home

import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalDate
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.home.TodaySessionsResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.employees.EmployeePermission
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.uuid.Uuid

/** Тесты read-проекции расписания на день: фильтр статуса, сортировка и подстановка названий. */
class DbTodayScheduleViewTest {
    private val orgId = OrgId.new()
    private val branchId = BranchId.new()
    private val view = DbTodayScheduleView()
    private val today = LocalDate(2026, 3, 17)

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .bind("id", orgId).bind("name", "Org").execute()
            TestPostgres.db.sql("INSERT INTO branches (id, org_id, name) VALUES (:id, :orgId, :name)")
                .bind("id", branchId).bind("orgId", orgId).bind("name", "Основной").execute()
        }
    }

    private val ctx =
        EmployeeRequestContext(
            lang = Lang.RU,
            userId = UserId.new(),
            orgId = orgId,
            branchId = branchId,
            employeeId = EmployeeId.new(),
            username = "test@example.com",
            clientIp = null,
            currency = Currency.RUB,
            permission = EmployeePermission(),
        )

    private suspend fun insertHall(name: String): Uuid {
        val id = Uuid.generateV7()
        TestPostgres.db
            .sql("INSERT INTO halls (id, org_id, branch_id, name) VALUES (:id, :orgId, :branchId, :name)")
            .bind("id", id).bind("orgId", orgId).bind("branchId", branchId).bind("name", name)
            .execute()
        return id
    }

    private suspend fun insertGroup(name: String): Uuid {
        val id = Uuid.generateV7()
        TestPostgres.db
            .sql("INSERT INTO groups (id, org_id, branch_id, name) VALUES (:id, :orgId, :branchId, :name)")
            .bind("id", id).bind("orgId", orgId).bind("branchId", branchId).bind("name", name)
            .execute()
        return id
    }

    private suspend fun insertSession(
        groupId: Uuid,
        hallId: Uuid,
        date: LocalDate = today,
        startTime: LocalTime,
        endTime: LocalTime,
        status: String = "scheduled",
    ) {
        TestPostgres.db
            .sql(
                """
                INSERT INTO sessions (id, org_id, group_id, hall_id, date, start_time, end_time, status)
                VALUES (:id, :orgId, :groupId, :hallId, :date, :startTime::time, :endTime::time, :status::session_status)
                """.trimIndent(),
            )
            .bind("id", Uuid.generateV7())
            .bind("orgId", orgId)
            .bind("groupId", groupId)
            .bind("hallId", hallId)
            .bind("date", date.toJavaLocalDate())
            .bind("startTime", startTime.toString())
            .bind("endTime", endTime.toString())
            .bind("status", status)
            .execute()
    }

    private suspend fun sessionsOn(date: LocalDate = today): TodaySessionsResponse =
        either {
            val raise: Raise<DomainError> = this
            TestPostgres.db.transaction {
                context(ctx, this, raise) { view.sessionsOn(date) }
            }
        }.getOrElse { fail("Unexpected error: $it") }

    @Test
    fun `пустой день возвращает пустой список с датой`() =
        runTest {
            val result = sessionsOn()
            assertEquals(today, result.date)
            assertTrue(result.sessions.isEmpty())
        }

    @Test
    fun `подставляет названия группы и зала`() =
        runTest {
            val group = insertGroup("Самбо")
            val hall = insertHall("Большой зал")
            insertSession(group, hall, startTime = LocalTime(10, 0), endTime = LocalTime(11, 0))

            val item = sessionsOn().sessions.single()

            assertEquals("Самбо", item.groupName)
            assertEquals("Большой зал", item.hallName)
            assertEquals(LocalTime(10, 0), item.startTime)
            assertEquals(LocalTime(11, 0), item.endTime)
        }

    @Test
    fun `возвращает занятия отсортированными по времени начала`() =
        runTest {
            val group = insertGroup("Самбо")
            val hall = insertHall("Зал")
            insertSession(group, hall, startTime = LocalTime(18, 0), endTime = LocalTime(19, 0))
            insertSession(group, hall, startTime = LocalTime(9, 0), endTime = LocalTime(10, 0))
            insertSession(group, hall, startTime = LocalTime(13, 0), endTime = LocalTime(14, 0))

            val times = sessionsOn().sessions.map { it.startTime }

            assertEquals(listOf(LocalTime(9, 0), LocalTime(13, 0), LocalTime(18, 0)), times)
        }

    @Test
    fun `не запланированные занятия отфильтровываются`() =
        runTest {
            val group = insertGroup("Самбо")
            val hall = insertHall("Зал")
            insertSession(group, hall, startTime = LocalTime(10, 0), endTime = LocalTime(11, 0), status = "cancelled")
            insertSession(group, hall, startTime = LocalTime(12, 0), endTime = LocalTime(13, 0))

            val result = sessionsOn()

            assertEquals(1, result.sessions.size)
            assertEquals(LocalTime(12, 0), result.sessions.single().startTime)
        }

    @Test
    fun `занятия других дней не попадают в выборку`() =
        runTest {
            val group = insertGroup("Самбо")
            val hall = insertHall("Зал")
            insertSession(group, hall, date = today, startTime = LocalTime(10, 0), endTime = LocalTime(11, 0))
            insertSession(group, hall, date = LocalDate(2026, 3, 18), startTime = LocalTime(10, 0), endTime = LocalTime(11, 0))

            assertEquals(1, sessionsOn().sessions.size)
        }
}
