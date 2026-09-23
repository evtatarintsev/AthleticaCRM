package org.athletica.crm.read.schedule

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.schedule.ScheduleListResponse
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.sessions.SessionStatus
import org.athletica.crm.schedule.ScheduleFixture
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Тесты read-проекции расписания: скоуп по филиалу, фильтры, названия и отсутствие записи. */
class DbScheduleViewTest {
    private val fixture = ScheduleFixture()
    private val view = DbScheduleView()
    private val monday = LocalDate(2026, 3, 16)
    private val sunday = LocalDate(2026, 3, 22)

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking { fixture.setUp() }
    }

    private suspend fun list(query: ScheduleQuery = ScheduleQuery(from = monday, to = sunday)): ScheduleListResponse = fixture.inContext { view.list(query) }

    private suspend fun insertDiscipline(name: String): DisciplineId {
        val id = DisciplineId.new()
        TestPostgres.db
            .sql("INSERT INTO disciplines (id, org_id, name) VALUES (:id, :orgId, :name)")
            .bind("id", id)
            .bind("orgId", fixture.orgId)
            .bind("name", name)
            .execute()
        return id
    }

    private suspend fun linkGroupDiscipline(
        groupId: GroupId,
        disciplineId: DisciplineId,
    ) {
        TestPostgres.db
            .sql("INSERT INTO group_disciplines (group_id, discipline_id) VALUES (:g, :d)")
            .bind("g", groupId)
            .bind("d", disciplineId)
            .execute()
    }

    /** Занятие с 10:00 до 11:00 в [date]. */
    private suspend fun session(
        groupId: GroupId,
        hallId: HallId,
        date: LocalDate = monday,
        status: String = "scheduled",
    ): SessionId = fixture.insertSession(groupId, hallId, date, LocalTime(10, 0), LocalTime(11, 0), status = status)

    @Test
    fun `возвращает занятия периода с названиями зала, тренеров и дисциплин`() =
        runTest {
            val group = fixture.insertGroup("Самбо дети")
            val hall = fixture.insertHall("Большой зал")
            val coachB = fixture.insertEmployee("Борис")
            val coachA = fixture.insertEmployee("Анна")
            val judo = insertDiscipline("Дзюдо")
            val sambo = insertDiscipline("Самбо")
            linkGroupDiscipline(group, sambo)
            linkGroupDiscipline(group, judo)
            val id = session(group, hall, status = "cancelled")
            fixture.linkSessionEmployee(id, coachB)
            fixture.linkSessionEmployee(id, coachA)

            val item = list().sessions.single()

            assertEquals(id, item.id)
            assertEquals(group, item.group.id)
            assertEquals("Самбо дети", item.group.name)
            assertEquals(monday, item.date)
            assertEquals(LocalTime(10, 0), item.startTime)
            assertEquals(LocalTime(11, 0), item.endTime)
            assertEquals(hall, item.hall.id)
            assertEquals("Большой зал", item.hall.name)
            assertEquals(listOf("Анна", "Борис"), item.coaches.map { it.name })
            assertEquals(listOf(coachA, coachB), item.coaches.map { it.id })
            assertEquals(listOf("Дзюдо", "Самбо"), item.disciplines.map { it.name })
            assertEquals(SessionStatus.CANCELLED, item.status)
            assertEquals(colorKeyFor(group, judo), item.colorKey)
        }

    @Test
    fun `занятие без тренеров и дисциплин отдаёт пустые списки`() =
        runTest {
            val group = fixture.insertGroup()
            session(group, fixture.insertHall())

            val item = list().sessions.single()

            assertTrue(item.coaches.isEmpty())
            assertTrue(item.disciplines.isEmpty())
            assertEquals(colorKeyFor(group, null), item.colorKey)
        }

    @Test
    fun `занятия вне периода не попадают в выдачу, порядок по дате и времени`() =
        runTest {
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()
            fixture.insertSession(group, hall, sunday, LocalTime(9, 0), LocalTime(10, 0))
            fixture.insertSession(group, hall, monday, LocalTime(18, 0), LocalTime(19, 0))
            fixture.insertSession(group, hall, monday, LocalTime(8, 0), LocalTime(9, 0))
            fixture.insertSession(group, hall, LocalDate(2026, 3, 15), LocalTime(8, 0), LocalTime(9, 0))
            fixture.insertSession(group, hall, LocalDate(2026, 3, 23), LocalTime(8, 0), LocalTime(9, 0))

            val result = list().sessions.map { it.date to it.startTime }

            assertEquals(
                listOf(monday to LocalTime(8, 0), monday to LocalTime(18, 0), sunday to LocalTime(9, 0)),
                result,
            )
        }

    @Test
    fun `занятия группы другого филиала не попадают в выдачу`() =
        runTest {
            val ownGroup = fixture.insertGroup("Свой")
            val hall = fixture.insertHall()
            session(ownGroup, hall)
            val otherBranch = Uuid.generateV7()
            TestPostgres.db
                .sql("INSERT INTO branches (id, org_id, name) VALUES (:id, :orgId, :name)")
                .bind("id", otherBranch)
                .bind("orgId", fixture.orgId)
                .bind("name", "Другой")
                .execute()
            val otherGroup = GroupId.new()
            TestPostgres.db
                .sql("INSERT INTO groups (id, org_id, branch_id, name) VALUES (:id, :orgId, :branchId, :name)")
                .bind("id", otherGroup)
                .bind("orgId", fixture.orgId)
                .bind("branchId", otherBranch)
                .bind("name", "Чужой")
                .execute()
            session(otherGroup, hall)

            assertEquals(listOf("Свой"), list().sessions.map { it.group.name })
        }

    @Test
    fun `значения одного фильтра объединяются по или, разные фильтры по и`() =
        runTest {
            val hallA = fixture.insertHall("A")
            val hallB = fixture.insertHall("B")
            val hallC = fixture.insertHall("C")
            val sambo = insertDiscipline("Самбо")
            val judo = insertDiscipline("Дзюдо")
            val coach = fixture.insertEmployee("Тренер")
            val samboGroup = fixture.insertGroup("Самбо")
            linkGroupDiscipline(samboGroup, sambo)
            val judoGroup = fixture.insertGroup("Дзюдо")
            linkGroupDiscipline(judoGroup, judo)
            val inA = session(samboGroup, hallA)
            val inB = session(judoGroup, hallB)
            val inC = session(samboGroup, hallC)
            val samboWithCoach = session(samboGroup, hallB, date = sunday)
            fixture.linkSessionEmployee(samboWithCoach, coach)
            fixture.linkSessionEmployee(inB, coach)

            val byHalls = list(ScheduleQuery(monday, sunday, hallIds = listOf(hallA, hallB))).sessions.map { it.id }.toSet()
            assertEquals(setOf(inA, inB, samboWithCoach), byHalls)

            val byDisciplineAndCoach =
                list(ScheduleQuery(monday, sunday, disciplineIds = listOf(sambo), employeeIds = listOf(coach)))
                    .sessions
                    .map { it.id }
            assertEquals(listOf(samboWithCoach), byDisciplineAndCoach)

            val unfiltered = list(ScheduleQuery(monday, sunday)).sessions.map { it.id }.toSet()
            assertEquals(setOf(inA, inB, inC, samboWithCoach), unfiltered)
        }

    @Test
    fun `фильтр по тренеру смотрит на состав занятия, а не группы`() =
        runTest {
            val group = fixture.insertGroup()
            val groupCoach = fixture.insertEmployee("Тренер группы")
            val replacement = fixture.insertEmployee("Замена")
            fixture.linkGroupEmployee(group, groupCoach)
            val id =
                fixture.insertSession(
                    group,
                    fixture.insertHall(),
                    monday,
                    LocalTime(10, 0),
                    LocalTime(11, 0),
                    isEmployeeAssignmentOverridden = true,
                )
            fixture.linkSessionEmployee(id, replacement)

            assertEquals(listOf(id), list(ScheduleQuery(monday, sunday, employeeIds = listOf(replacement))).sessions.map { it.id })
            assertTrue(list(ScheduleQuery(monday, sunday, employeeIds = listOf(groupCoach))).sessions.isEmpty())
        }

    @Test
    fun `занятие без группы исключается`() =
        runTest {
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()
            val kept = session(group, hall)
            val orphan = session(group, hall, date = sunday)
            TestPostgres.db
                .sql("UPDATE sessions SET group_id = NULL WHERE id = :id")
                .bind("id", orphan)
                .execute()

            assertEquals(listOf(kept), list().sessions.map { it.id })
        }

    @Test
    fun `запрос ничего не создаёт и повторяется с тем же составом`() =
        runTest {
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()
            fixture.insertSlot(group, hall, org.athletica.crm.core.DayOfWeek.MONDAY, LocalTime(9, 0), LocalTime(10, 0), from = monday)
            session(group, hall)
            val before = fixture.sessionCount()

            val first = list()
            val second = list()

            assertEquals(before, fixture.sessionCount())
            assertEquals(first, second)
            assertEquals(1, first.sessions.size)
        }
}
