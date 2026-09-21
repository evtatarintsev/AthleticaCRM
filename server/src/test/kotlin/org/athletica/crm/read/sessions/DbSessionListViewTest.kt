package org.athletica.crm.read.sessions

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toKotlinLocalDate
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.DayOfWeek
import org.athletica.crm.schedule.ScheduleFixture
import org.athletica.crm.schedule.plusDays
import org.athletica.crm.storage.asLong
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Тесты проекции списка занятий: выборка по периоду и группе, и неизменность данных при чтении. */
class DbSessionListViewTest {
    private val view = DbSessionListView()
    private val fixture = ScheduleFixture()
    private val today = java.time.LocalDate.now().toKotlinLocalDate()

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    /** Количество занятий в базе — для проверки, что чтение ничего не создаёт. */
    private suspend fun rowCount(): Long =
        TestPostgres.db
            .sql("SELECT COUNT(*) AS cnt FROM sessions")
            .firstOrNull { it.asLong("cnt") } ?: 0L

    @Test
    fun `возвращает занятия периода с названием группы и тренерами`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup("Йога")
            val hall = fixture.insertHall()
            val coach = fixture.insertEmployee("Анна")
            val slot = fixture.insertSlot(group, hall, DayOfWeek.MONDAY, LocalTime(10, 0), LocalTime(11, 0), today)
            val session = fixture.insertSession(group, hall, today, LocalTime(10, 0), LocalTime(11, 0), originSlotId = slot)
            fixture.linkSessionEmployee(session, coach)

            val result = fixture.inContext { view.list(SessionListQuery(today, today.plusDays(7))) }

            val item = result.sessions.single()
            assertEquals("Йога", item.groupName)
            assertEquals(listOf(coach), item.employeeIds)
            assertEquals(false, item.isManual)
        }

    @Test
    fun `ручное занятие отмечено признаком isManual`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()
            fixture.insertSession(group, hall, today, LocalTime(10, 0), LocalTime(11, 0))

            val result = fixture.inContext { view.list(SessionListQuery(today, today)) }

            assertTrue(result.sessions.single().isManual)
        }

    @Test
    fun `фильтрует по периоду и группе`() =
        runTest {
            fixture.setUp()
            val yoga = fixture.insertGroup("Йога")
            val boxing = fixture.insertGroup("Бокс")
            val hall = fixture.insertHall()
            fixture.insertSession(yoga, hall, today, LocalTime(10, 0), LocalTime(11, 0))
            fixture.insertSession(boxing, hall, today, LocalTime(12, 0), LocalTime(13, 0))
            fixture.insertSession(yoga, hall, today.plusDays(10), LocalTime(10, 0), LocalTime(11, 0))

            val byPeriod = fixture.inContext { view.list(SessionListQuery(today, today.plusDays(1))) }
            val byGroup = fixture.inContext { view.list(SessionListQuery(today, today.plusDays(30), yoga)) }

            assertEquals(2, byPeriod.sessions.size)
            assertEquals(2, byGroup.sessions.size)
            assertTrue(byGroup.sessions.all { it.groupId == yoga })
        }

    @Test
    fun `запрос списка не создаёт и не изменяет занятия`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()
            fixture.insertSlot(group, hall, DayOfWeek.MONDAY, LocalTime(10, 0), LocalTime(11, 0), today)
            val before = rowCount()

            val first = fixture.inContext { view.list(SessionListQuery(today, today.plusDays(60))) }
            val second = fixture.inContext { view.list(SessionListQuery(today, today.plusDays(60))) }

            assertEquals(before, rowCount())
            assertEquals(first.sessions, second.sessions)
        }

    @Test
    fun `период без занятий отдаёт пустой список`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()
            fixture.insertSession(group, hall, today, LocalTime(10, 0), LocalTime(11, 0))

            val result = fixture.inContext { view.list(SessionListQuery(today.plusDays(10), today.plusDays(20))) }

            assertEquals(emptyList(), result.sessions)
            assertEquals(1L, rowCount())
        }
}
