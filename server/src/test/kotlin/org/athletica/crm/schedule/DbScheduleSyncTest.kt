package org.athletica.crm.schedule

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toKotlinLocalDate
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.DayOfWeek
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.entityids.SlotId
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.entityids.toHallId
import org.athletica.crm.core.entityids.toSessionId
import org.athletica.crm.domain.groups.DbGroupSchedule
import org.athletica.crm.domain.groups.NewSlot
import org.athletica.crm.domain.sessions.DbScheduleSync
import org.athletica.crm.storage.asLocalDate
import org.athletica.crm.storage.asLocalTime
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Тесты сверки расписания и занятий: создание, обновление и удаление нетронутых занятий,
 * неприкосновенность изменённых человеком и прошедших, идемпотентность и граничные даты.
 */
class DbScheduleSyncTest {
    private val sync = DbScheduleSync()
    private val schedule = DbGroupSchedule()
    private val fixture = ScheduleFixture()
    private val today = java.time.LocalDate.now().toKotlinLocalDate()

    /** Ближайший понедельник не раньше сегодняшнего дня. */
    private val nextMonday = generateSequence(today) { it.plusDays(1) }.first { it.dayOfWeek.name == "MONDAY" }

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    private suspend fun sync() = fixture.inContext { sync.sync() }

    private suspend fun setSchedule(groupId: GroupId, from: LocalDate?, slots: List<NewSlot>) = fixture.inContext { schedule.setFrom(groupId, from, slots) }

    /** Занятия группы, упорядоченные по дате. */
    private suspend fun sessions(groupId: GroupId): List<SessionRow> =
        TestPostgres.db
            .sql(
                """
                SELECT id, date, start_time, end_time, hall_id, status, origin_slot_id
                FROM sessions WHERE group_id = :groupId ORDER BY date, start_time
                """.trimIndent(),
            )
            .bind("groupId", groupId)
            .list { row ->
                SessionRow(
                    id = row.asUuid("id").toSessionId(),
                    date = row.asLocalDate("date"),
                    startTime = row.asLocalTime("start_time"),
                    endTime = row.asLocalTime("end_time"),
                    hallId = row.asUuid("hall_id").toHallId(),
                    status = row.asString("status"),
                    originSlotId = row.asUuidOrNull("origin_slot_id"),
                )
            }

    /** Тренеры занятия [sessionId]. */
    private suspend fun employeesOf(sessionId: SessionId): List<EmployeeId> =
        TestPostgres.db
            .sql("SELECT employee_id FROM session_employees WHERE session_id = :id ORDER BY employee_id")
            .bind("id", sessionId)
            .list { it.asUuid("employee_id").toEmployeeId() }

    private fun mondayAt10(hall: HallId, endAt: LocalTime = LocalTime(11, 0)) = NewSlot(DayOfWeek.MONDAY, LocalTime(10, 0), endAt, hall)

    @Test
    fun `занятия создаются на все подходящие даты периода внутри горизонта`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()
            setSchedule(group, today, listOf(mondayAt10(hall)))

            sync()

            val dates = sessions(group).map { it.date }
            assertTrue(dates.isNotEmpty())
            assertTrue(dates.all { it.dayOfWeek.name == "MONDAY" })
            assertEquals(dates.sorted(), dates)
            assertTrue(dates.first() >= today)
            assertTrue(dates.last() <= today.plusDays(93), "занятие за горизонтом: ${dates.last()}")
            assertTrue(dates.last() >= today.plusDays(80), "горизонт короче трёх месяцев: ${dates.last()}")
        }

    @Test
    fun `занятия не выходят за период действия слота`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()
            fixture.insertSlot(group, hall, DayOfWeek.MONDAY, LocalTime(10, 0), LocalTime(11, 0), nextMonday, nextMonday.plusDays(14))

            sync()

            assertEquals(listOf(nextMonday, nextMonday.plusDays(7)), sessions(group).map { it.date })
        }

    @Test
    fun `слот целиком за горизонтом не порождает занятий`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()
            fixture.insertSlot(group, hall, DayOfWeek.MONDAY, LocalTime(10, 0), LocalTime(11, 0), today.plusDays(200))

            sync()

            assertEquals(emptyList(), sessions(group).map { it.date })
        }

    @Test
    fun `слот целиком в прошлом не порождает занятий`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()
            fixture.insertSlot(group, hall, DayOfWeek.MONDAY, LocalTime(10, 0), LocalTime(11, 0), today.plusDays(-60), today.plusDays(-30))

            sync()

            assertEquals(emptyList(), sessions(group).map { it.date })
        }

    @Test
    fun `смена зала и времени окончания обновляет нетронутые занятия, не меняя их дат`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hallA = fixture.insertHall("A")
            val hallB = fixture.insertHall("B")
            val slot = fixture.insertSlot(group, hallA, DayOfWeek.MONDAY, LocalTime(10, 0), LocalTime(11, 0), nextMonday)
            sync()
            val before = sessions(group)

            TestPostgres.db
                .sql("UPDATE schedule_slots SET hall_id = :hall, end_time = '12:00'::time WHERE id = :id")
                .bind("hall", hallB)
                .bind("id", slot)
                .execute()
            sync()

            val after = sessions(group)
            assertEquals(before.map { it.id }, after.map { it.id })
            assertEquals(before.map { it.date }, after.map { it.date })
            assertTrue(after.all { it.hallId == hallB })
            assertTrue(after.all { it.endTime == LocalTime(12, 0) })
        }

    @Test
    fun `исключение слота удаляет нетронутые будущие занятия, не оставляя отменённых`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()
            setSchedule(group, today, listOf(mondayAt10(hall)))
            sync()
            assertTrue(sessions(group).isNotEmpty())

            setSchedule(group, today, emptyList())
            sync()

            assertEquals(emptyList(), sessions(group).map { it.date })
        }

    @Test
    fun `расписание закрыто с будущей даты — занятия до неё сохраняются`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()
            setSchedule(group, today, listOf(mondayAt10(hall)))
            sync()

            val boundary = nextMonday.plusDays(14)
            setSchedule(group, boundary, emptyList())
            sync()

            val dates = sessions(group).map { it.date }
            assertTrue(dates.all { it < boundary }, "занятие на закрытом периоде: $dates")
            assertTrue(dates.contains(nextMonday))
        }

    @Test
    fun `смена тренеров группы меняет будущие занятия и не трогает прошедшие`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()
            val oldCoach = fixture.insertEmployee("Старый")
            val newCoach = fixture.insertEmployee("Новый")
            fixture.linkGroupEmployee(group, oldCoach)
            val pastSlot =
                fixture.insertSlot(group, hall, DayOfWeek.MONDAY, LocalTime(10, 0), LocalTime(11, 0), today.plusDays(-60), today.plusDays(-30))
            val past =
                fixture.insertSession(
                    group,
                    hall,
                    today.plusDays(-35),
                    LocalTime(10, 0),
                    LocalTime(11, 0),
                    originSlotId = pastSlot,
                )
            fixture.linkSessionEmployee(past, oldCoach)
            setSchedule(group, today, listOf(mondayAt10(hall)))
            sync()
            val future = sessions(group).first { it.date >= today }
            assertEquals(listOf(oldCoach), employeesOf(future.id))

            TestPostgres.db.sql("DELETE FROM group_employees WHERE group_id = :g").bind("g", group).execute()
            fixture.linkGroupEmployee(group, newCoach)
            sync()

            assertEquals(listOf(newCoach), employeesOf(future.id))
            assertEquals(listOf(oldCoach), employeesOf(past))
        }

    @Test
    fun `отменённое занятие переживает смену расписания и не воскресает`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()
            setSchedule(group, today, listOf(mondayAt10(hall)))
            sync()
            val target = sessions(group).first { it.date >= today }
            TestPostgres.db.sql("UPDATE sessions SET status = 'cancelled' WHERE id = :id").bind("id", target.id).execute()

            sync()
            setSchedule(group, today, emptyList())
            sync()

            val remaining = sessions(group)
            assertEquals(listOf(target.id), remaining.map { it.id })
            assertEquals("cancelled", remaining.single().status)
        }

    @Test
    fun `перенесённое занятие сохраняется на назначенной дате`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()
            setSchedule(group, today, listOf(mondayAt10(hall)))
            sync()
            val target = sessions(group).first { it.date >= today }
            val movedTo = target.date.plusDays(2)
            TestPostgres.db
                .sql("UPDATE sessions SET is_rescheduled = true, date = :date WHERE id = :id")
                .bind("date", movedTo)
                .bind("id", target.id)
                .execute()

            setSchedule(group, today, emptyList())
            sync()

            val remaining = sessions(group).single()
            assertEquals(target.id, remaining.id)
            assertEquals(movedTo, remaining.date)
        }

    @Test
    fun `занятие с переопределёнными тренерами не перезаписывается`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()
            val groupCoach = fixture.insertEmployee("Групповой")
            val ownCoach = fixture.insertEmployee("Свой")
            fixture.linkGroupEmployee(group, groupCoach)
            setSchedule(group, today, listOf(mondayAt10(hall)))
            sync()
            val target = sessions(group).first { it.date >= today }
            TestPostgres.db.sql("DELETE FROM session_employees WHERE session_id = :id").bind("id", target.id).execute()
            fixture.linkSessionEmployee(target.id, ownCoach)
            TestPostgres.db
                .sql("UPDATE sessions SET is_employee_assignment_overridden = true WHERE id = :id")
                .bind("id", target.id)
                .execute()

            sync()

            assertEquals(listOf(ownCoach), employeesOf(target.id))
        }

    @Test
    fun `занятие с заметкой переживает смену расписания`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()
            setSchedule(group, today, listOf(mondayAt10(hall)))
            sync()
            val target = sessions(group).first { it.date >= today }
            TestPostgres.db.sql("UPDATE sessions SET notes = 'важно' WHERE id = :id").bind("id", target.id).execute()

            setSchedule(group, today, emptyList())
            sync()

            assertEquals(listOf(target.id), sessions(group).map { it.id })
        }

    @Test
    fun `ручное занятие переживает сверку`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()
            val manual = fixture.insertSession(group, hall, today.plusDays(3), LocalTime(18, 0), LocalTime(19, 0))
            setSchedule(group, today, listOf(mondayAt10(hall)))

            sync()
            setSchedule(group, today, emptyList())
            sync()

            assertEquals(listOf(manual), sessions(group).map { it.id })
        }

    @Test
    fun `прошедшие занятия не меняются при полной замене расписания`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hallA = fixture.insertHall("A")
            val hallB = fixture.insertHall("B")
            val pastSlot =
                fixture.insertSlot(group, hallA, DayOfWeek.MONDAY, LocalTime(10, 0), LocalTime(11, 0), today.plusDays(-60), today.plusDays(-30))
            val past =
                fixture.insertSession(group, hallA, today.plusDays(-35), LocalTime(10, 0), LocalTime(11, 0), originSlotId = pastSlot)

            setSchedule(group, today, listOf(mondayAt10(hallB, LocalTime(13, 0))))
            sync()

            val row = sessions(group).single { it.id == past }
            assertEquals(hallA, row.hallId)
            assertEquals(LocalTime(11, 0), row.endTime)
            assertEquals(today.plusDays(-35), row.date)
        }

    @Test
    fun `две сверки подряд не меняют количество занятий`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()
            setSchedule(group, today, listOf(mondayAt10(hall)))

            sync()
            val first = sessions(group)
            sync()

            assertEquals(first.map { it.id }, sessions(group).map { it.id })
        }

    @Test
    fun `смена расписания ровно на дату занятия переносит границу на этот день`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hallA = fixture.insertHall("A")
            val hallB = fixture.insertHall("B")
            setSchedule(group, today, listOf(mondayAt10(hallA)))
            sync()
            val boundary = sessions(group).first { it.date > today }.date

            setSchedule(group, boundary, listOf(mondayAt10(hallB)))
            sync()

            val rows = sessions(group)
            assertTrue(rows.filter { it.date < boundary }.all { it.hallId == hallA })
            assertTrue(rows.filter { it.date >= boundary }.all { it.hallId == hallB })
            assertTrue(rows.any { it.date == boundary })
        }

    @Test
    fun `смена расписания между занятиями не трогает предыдущее`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hallA = fixture.insertHall("A")
            val hallB = fixture.insertHall("B")
            setSchedule(group, today, listOf(mondayAt10(hallA)))
            sync()
            val second = sessions(group).first { it.date > today.plusDays(1) }
            val between = second.date.plusDays(-3)

            setSchedule(group, between, listOf(mondayAt10(hallB)))
            sync()

            val rows = sessions(group)
            assertTrue(rows.filter { it.date < between }.all { it.hallId == hallA })
            assertTrue(rows.filter { it.date >= between }.all { it.hallId == hallB })
        }

    @Test
    fun `удаление ещё не вступившей в силу версии с ручной правкой проходит`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hallA = fixture.insertHall("A")
            val hallB = fixture.insertHall("B")
            setSchedule(group, today, listOf(mondayAt10(hallA)))
            val planned = nextMonday.plusDays(14)
            setSchedule(group, planned, listOf(mondayAt10(hallB)))
            sync()
            val plannedSlot: SlotId = fixture.inContext { schedule.slotsOn(group, planned) }.single().id
            val edited = sessions(group).first { it.originSlotId == plannedSlot.value }
            TestPostgres.db.sql("UPDATE sessions SET notes = 'правка' WHERE id = :id").bind("id", edited.id).execute()

            setSchedule(group, today.plusDays(1), listOf(mondayAt10(hallA)))
            sync()

            val survivor = sessions(group).single { it.id == edited.id }
            assertEquals(null, survivor.originSlotId)
        }

    @Test
    fun `сверка системным контекстом восполняет рассогласование`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()
            setSchedule(group, today, listOf(mondayAt10(hall)))
            sync()
            val expected = sessions(group).map { it.date }
            TestPostgres.db.sql("DELETE FROM sessions WHERE group_id = :g AND date > :d").bind("g", group).bind("d", today).execute()
            assertTrue(sessions(group).size < expected.size)

            val systemContext = org.athletica.crm.core.systemContext(org.athletica.crm.core.entityids.OrgId(fixture.orgId))
            arrow.core.raise.either {
                val raise: arrow.core.raise.Raise<org.athletica.crm.core.errors.DomainError> = this
                TestPostgres.db.transaction {
                    context(systemContext, this, raise) { sync.sync() }
                }
            }

            assertEquals(expected, sessions(group).map { it.date })
        }
}

/** Строка занятия для проверок в тестах сверки. */
private data class SessionRow(
    val id: SessionId,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val hallId: HallId,
    val status: String,
    val originSlotId: kotlin.uuid.Uuid?,
)
