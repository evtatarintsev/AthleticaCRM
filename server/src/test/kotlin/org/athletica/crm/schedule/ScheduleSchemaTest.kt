package org.athletica.crm.schedule

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.DayOfWeek
import org.athletica.crm.core.entityids.SlotId
import org.athletica.crm.storage.asInt
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Тесты инвариантов схемы расписания и занятий: сопоставление дня недели с ISO-номером,
 * непересечение версий слота, уникальность занятия по происхождению и запрет
 * удаления группы с занятиями.
 */
class ScheduleSchemaTest {
    private val fixture = ScheduleFixture()
    private val monday = kotlinx.datetime.LocalDate(2026, 1, 5)

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    @Test
    fun `isodow_of переводит день недели в ISO-номер`() =
        runTest {
            val numbers =
                DayOfWeek.entries.map { day ->
                    TestPostgres.db
                        .sql("SELECT isodow_of(:day::day_of_week) AS n")
                        .bind("day", day.name)
                        .firstOrNull { it.asInt("n") }
                }

            assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), numbers)
        }

    @Test
    fun `пересекающаяся версия слота отклоняется`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()
            fixture.insertSlot(group, hall, DayOfWeek.MONDAY, LocalTime(10, 0), LocalTime(11, 0), monday)

            val error =
                runCatching {
                    fixture.insertSlot(
                        group,
                        hall,
                        DayOfWeek.MONDAY,
                        LocalTime(10, 0),
                        LocalTime(12, 0),
                        monday.plusDays(60),
                    )
                }.exceptionOrNull()

            assertTrue(error?.message?.contains("no_overlapping_slot_versions") == true, "ожидалось нарушение exclusion constraint, было: $error")
        }

    @Test
    fun `непересекающаяся версия слота проходит`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()
            val first =
                fixture.insertSlot(
                    group,
                    hall,
                    DayOfWeek.MONDAY,
                    LocalTime(10, 0),
                    LocalTime(11, 0),
                    monday,
                    monday.plusDays(60),
                )

            val second =
                fixture.insertSlot(
                    group,
                    hall,
                    DayOfWeek.MONDAY,
                    LocalTime(10, 0),
                    LocalTime(12, 0),
                    monday.plusDays(60),
                )

            assertTrue(first != second)
        }

    @Test
    fun `дубль занятия по паре слот-дата отклоняется`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()
            val slot = fixture.insertSlot(group, hall, DayOfWeek.MONDAY, LocalTime(10, 0), LocalTime(11, 0), monday)
            fixture.insertSession(group, hall, monday, LocalTime(10, 0), LocalTime(11, 0), originSlotId = slot)

            val error =
                runCatching {
                    fixture.insertSession(group, hall, monday, LocalTime(10, 0), LocalTime(11, 0), originSlotId = slot)
                }.exceptionOrNull()

            assertTrue(error?.message?.contains("uq_sessions_origin") == true, "ожидалось нарушение уникальности, было: $error")
        }

    @Test
    fun `два ручных занятия на одну дату допустимы`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()

            fixture.insertSession(group, hall, monday, LocalTime(10, 0), LocalTime(11, 0))
            val second = fixture.insertSession(group, hall, monday, LocalTime(10, 0), LocalTime(11, 0))

            assertNotNull(second)
        }

    @Test
    fun `удаление группы с занятиями отклоняется`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()
            fixture.insertSession(group, hall, monday, LocalTime(10, 0), LocalTime(11, 0))

            val error =
                runCatching {
                    TestPostgres.db.sql("DELETE FROM groups WHERE id = :id").bind("id", group).execute()
                }.exceptionOrNull()

            assertTrue(error?.message?.contains("sessions_group_id_fkey") == true, "ожидался RESTRICT по занятиям, было: $error")
        }

    @Test
    fun `удаление версии слота с занятиями отклоняется`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()
            val slot: SlotId = fixture.insertSlot(group, hall, DayOfWeek.MONDAY, LocalTime(10, 0), LocalTime(11, 0), monday)
            fixture.insertSession(group, hall, monday, LocalTime(10, 0), LocalTime(11, 0), originSlotId = slot)

            val error =
                runCatching {
                    TestPostgres.db.sql("DELETE FROM schedule_slots WHERE id = :id").bind("id", slot).execute()
                }.exceptionOrNull()

            assertTrue(error?.message?.contains("origin_slot_id") == true, "ожидался RESTRICT по занятиям, было: $error")
        }

    @Test
    fun `вырожденный период действия отклоняется`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()

            val error =
                runCatching {
                    fixture.insertSlot(group, hall, DayOfWeek.MONDAY, LocalTime(10, 0), LocalTime(11, 0), monday, monday)
                }.exceptionOrNull()

            assertTrue(error?.message?.contains("check_validity") == true, "ожидался CHECK на период действия, было: $error")
        }
}

/** Прибавляет [days] дней к дате — короткая запись для граничных случаев в тестах. */
internal fun kotlinx.datetime.LocalDate.plusDays(days: Int): kotlinx.datetime.LocalDate = kotlinx.datetime.LocalDate.fromEpochDays(toEpochDays() + days)
