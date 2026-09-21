package org.athletica.crm.schedule

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toKotlinLocalDate
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.DayOfWeek
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.SlotId
import org.athletica.crm.domain.groups.DbGroupSchedule
import org.athletica.crm.domain.groups.NewSlot
import org.athletica.crm.domain.groups.ScheduleSlot
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Тесты расписания как истории правил: чтение на дату, установка расписания с даты,
 * отмена отложенных изменений, закрытие и возобновление расписания.
 */
class DbGroupScheduleTest {
    private val schedule = DbGroupSchedule()
    private val fixture = ScheduleFixture()
    private val today = java.time.LocalDate.now().toKotlinLocalDate()

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    /** Правило «понедельник 10:00–11:00» в зале [hall]. */
    private fun mondayAt10(hall: HallId, endAt: LocalTime = LocalTime(11, 0)) = NewSlot(DayOfWeek.MONDAY, LocalTime(10, 0), endAt, hall)

    private suspend fun slotsOn(groupId: GroupId, date: LocalDate): List<ScheduleSlot> = fixture.inContext { schedule.slotsOn(groupId, date) }

    private suspend fun setFrom(groupId: GroupId, from: LocalDate?, slots: List<NewSlot>) = fixture.runInContext { schedule.setFrom(groupId, from, slots) }

    private suspend fun setFromOrFail(groupId: GroupId, from: LocalDate?, slots: List<NewSlot>) = fixture.inContext { schedule.setFrom(groupId, from, slots) }

    @Test
    fun `slotsOn возвращает только версию, действующую на дату`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hallA = fixture.insertHall("A")
            val hallB = fixture.insertHall("B")
            fixture.insertSlot(group, hallA, DayOfWeek.MONDAY, LocalTime(10, 0), LocalTime(11, 0), today.plusDays(-60), today.plusDays(10))
            fixture.insertSlot(group, hallB, DayOfWeek.MONDAY, LocalTime(10, 0), LocalTime(11, 0), today.plusDays(10))

            assertEquals(listOf(hallA), slotsOn(group, today).map { it.hallId })
            assertEquals(listOf(hallA), slotsOn(group, today.plusDays(9)).map { it.hallId })
            assertEquals(listOf(hallB), slotsOn(group, today.plusDays(10)).map { it.hallId })
            assertEquals(emptyList(), slotsOn(group, today.plusDays(-61)).map { it.hallId })
        }

    @Test
    fun `slotsDuring возвращает версии, пересекающие период`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hallA = fixture.insertHall("A")
            val hallB = fixture.insertHall("B")
            fixture.insertSlot(group, hallA, DayOfWeek.MONDAY, LocalTime(10, 0), LocalTime(11, 0), today.plusDays(-60), today.plusDays(10))
            fixture.insertSlot(group, hallB, DayOfWeek.MONDAY, LocalTime(10, 0), LocalTime(11, 0), today.plusDays(10))

            val crossing = fixture.inContext { schedule.slotsDuring(group, today.plusDays(5), today.plusDays(15)) }
            val onlyFirst = fixture.inContext { schedule.slotsDuring(group, today, today.plusDays(5)) }

            assertEquals(listOf(hallA, hallB), crossing.map { it.hallId })
            assertEquals(listOf(hallA), onlyFirst.map { it.hallId })
        }

    @Test
    fun `исчезнувший слот прекращает действие с даты, история сохраняется`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()
            fixture.insertSlot(group, hall, DayOfWeek.MONDAY, LocalTime(10, 0), LocalTime(11, 0), today.plusDays(-30))

            setFromOrFail(group, today.plusDays(10), emptyList())

            assertEquals(1, slotsOn(group, today.plusDays(9)).size)
            assertEquals(0, slotsOn(group, today.plusDays(10)).size)
            assertEquals(1, slotsOn(group, today.plusDays(-30)).size)
        }

    @Test
    fun `изменившийся слот заменяется новой версией с даты`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hallA = fixture.insertHall("A")
            val hallB = fixture.insertHall("B")
            val original = fixture.insertSlot(group, hallA, DayOfWeek.MONDAY, LocalTime(10, 0), LocalTime(11, 0), today.plusDays(-30))

            setFromOrFail(group, today.plusDays(10), listOf(mondayAt10(hallB)))

            assertEquals(listOf(hallA), slotsOn(group, today.plusDays(9)).map { it.hallId })
            assertEquals(listOf(hallB), slotsOn(group, today.plusDays(10)).map { it.hallId })
            assertEquals(original, slotsOn(group, today).single().id)
            assertTrue(slotsOn(group, today.plusDays(10)).single().id != original)
        }

    @Test
    fun `совпавший слот сохраняет версию и период`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()
            val original: SlotId =
                fixture.insertSlot(group, hall, DayOfWeek.MONDAY, LocalTime(10, 0), LocalTime(11, 0), today.plusDays(-30))

            setFromOrFail(group, today.plusDays(10), listOf(mondayAt10(hall)))

            val slot = slotsOn(group, today.plusDays(10)).single()
            assertEquals(original, slot.id)
            assertEquals(today.plusDays(-30), slot.validity.from)
            assertEquals(null, slot.validity.to)
        }

    @Test
    fun `добавленный слот начинает действовать с даты и не действует раньше`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()

            setFromOrFail(group, today.plusDays(10), listOf(mondayAt10(hall)))

            assertEquals(0, slotsOn(group, today.plusDays(9)).size)
            assertEquals(1, slotsOn(group, today.plusDays(10)).size)
        }

    @Test
    fun `отложенное изменение с более поздней даты отменяется установкой с более ранней`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hallA = fixture.insertHall("A")
            val hallB = fixture.insertHall("B")
            val hallC = fixture.insertHall("C")
            fixture.insertSlot(group, hallA, DayOfWeek.MONDAY, LocalTime(10, 0), LocalTime(11, 0), today.plusDays(-30), today.plusDays(90))
            fixture.insertSlot(group, hallB, DayOfWeek.MONDAY, LocalTime(10, 0), LocalTime(11, 0), today.plusDays(90))

            setFromOrFail(group, today.plusDays(20), listOf(mondayAt10(hallC)))

            assertEquals(listOf(hallA), slotsOn(group, today.plusDays(19)).map { it.hallId })
            assertEquals(listOf(hallC), slotsOn(group, today.plusDays(20)).map { it.hallId })
            assertEquals(listOf(hallC), slotsOn(group, today.plusDays(120)).map { it.hallId })
        }

    @Test
    fun `более позднее изменение поверх раннего сохраняет раннее`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hallA = fixture.insertHall("A")
            val hallB = fixture.insertHall("B")

            setFromOrFail(group, today.plusDays(20), listOf(mondayAt10(hallA)))
            setFromOrFail(group, today.plusDays(90), listOf(mondayAt10(hallB)))

            assertEquals(listOf(hallA), slotsOn(group, today.plusDays(20)).map { it.hallId })
            assertEquals(listOf(hallA), slotsOn(group, today.plusDays(89)).map { it.hallId })
            assertEquals(listOf(hallB), slotsOn(group, today.plusDays(90)).map { it.hallId })
        }

    @Test
    fun `дата вступления в силу в прошлом отклоняется`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()

            val result = setFrom(group, today.plusDays(-1), listOf(mondayAt10(hall)))

            assertEquals("SCHEDULE_EFFECTIVE_FROM_IN_PAST", assertIs<Either.Left<*>>(result).value.let { (it as org.athletica.crm.core.errors.DomainError).code })
            assertEquals(0, slotsOn(group, today).size)
        }

    @Test
    fun `дата не указана — изменение вступает в силу сегодня`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()

            setFromOrFail(group, null, listOf(mondayAt10(hall)))

            assertEquals(today, slotsOn(group, today).single().validity.from)
        }

    @Test
    fun `летний перерыв и возобновление`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hallA = fixture.insertHall("A")
            val hallB = fixture.insertHall("B")
            fixture.insertSlot(group, hallA, DayOfWeek.MONDAY, LocalTime(10, 0), LocalTime(11, 0), today.plusDays(-30))

            setFromOrFail(group, today.plusDays(30), emptyList())
            setFromOrFail(group, today.plusDays(120), listOf(mondayAt10(hallB)))

            assertEquals(listOf(hallA), slotsOn(group, today.plusDays(15)).map { it.hallId })
            assertEquals(emptyList(), slotsOn(group, today.plusDays(60)).map { it.hallId })
            assertEquals(listOf(hallB), slotsOn(group, today.plusDays(130)).map { it.hallId })
        }

    @Test
    fun `дубль по дню недели и времени начала отклоняется`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()

            val result = setFrom(group, today, listOf(mondayAt10(hall), mondayAt10(hall, LocalTime(12, 0))))

            assertEquals("DUPLICATE_SCHEDULE_SLOT", assertIs<Either.Left<org.athletica.crm.core.errors.DomainError>>(result).value.code)
            assertEquals(0, slotsOn(group, today).size)
        }

    @Test
    fun `слот с временем окончания не позже начала отклоняется`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()

            val result = setFrom(group, today, listOf(NewSlot(DayOfWeek.MONDAY, LocalTime(10, 0), LocalTime(10, 0), hall)))

            assertEquals("INVALID_SCHEDULE_TIME", assertIs<Either.Left<org.athletica.crm.core.errors.DomainError>>(result).value.code)
            assertEquals(0, slotsOn(group, today).size)
        }

    @Test
    fun `повторная установка того же расписания сохраняет версии`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()

            setFromOrFail(group, today, listOf(mondayAt10(hall)))
            val first = slotsOn(group, today).single().id
            setFromOrFail(group, today, listOf(mondayAt10(hall)))

            assertEquals(first, slotsOn(group, today).single().id)
        }
}
