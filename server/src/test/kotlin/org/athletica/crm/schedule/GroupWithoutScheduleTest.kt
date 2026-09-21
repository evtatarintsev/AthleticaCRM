package org.athletica.crm.schedule

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toKotlinLocalDate
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.DayOfWeek
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.domain.groups.DbGroupSchedule
import org.athletica.crm.domain.groups.DbGroups
import org.athletica.crm.domain.groups.NewSlot
import org.athletica.crm.storage.asLong
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Тесты независимости расписания от группы: изменение сведений о группе не трогает
 * ни действующие слоты, ни историю их версий, а создание группы не требует расписания.
 */
class GroupWithoutScheduleTest {
    private val groups = DbGroups()
    private val schedule = DbGroupSchedule()
    private val fixture = ScheduleFixture()
    private val today = java.time.LocalDate.now().toKotlinLocalDate()

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    /** Все версии слотов группы, включая закрытые. */
    private suspend fun versionCount(groupId: GroupId): Long =
        TestPostgres.db
            .sql("SELECT COUNT(*) AS cnt FROM schedule_slots WHERE group_id = :g")
            .bind("g", groupId)
            .firstOrNull { it.asLong("cnt") } ?: 0L

    @Test
    fun `переименование группы не меняет её расписание и историю версий`() =
        runTest {
            fixture.setUp()
            val hallA = fixture.insertHall("A")
            val hallB = fixture.insertHall("B")
            val groupId = GroupId.new()
            fixture.inContext { groups.new(groupId, "Йога", emptyList(), emptyList()) }
            fixture.inContext { schedule.setFrom(groupId, today, listOf(NewSlot(DayOfWeek.MONDAY, LocalTime(10, 0), LocalTime(11, 0), hallA))) }
            fixture.inContext {
                schedule.setFrom(groupId, today.plusDays(30), listOf(NewSlot(DayOfWeek.MONDAY, LocalTime(10, 0), LocalTime(11, 0), hallB)))
            }
            val before = fixture.inContext { schedule.slotsOn(groupId, today) }
            val versionsBefore = versionCount(groupId)

            fixture.inContext { groups.byId(groupId).withNewName("Йога продвинутая").save() }

            assertEquals(before, fixture.inContext { schedule.slotsOn(groupId, today) })
            assertEquals(versionsBefore, versionCount(groupId))
            assertEquals(listOf(hallB), fixture.inContext { schedule.slotsOn(groupId, today.plusDays(30)) }.map { it.hallId })
        }

    @Test
    fun `группа создаётся без расписания`() =
        runTest {
            fixture.setUp()
            val groupId = GroupId.new()

            fixture.inContext { groups.new(groupId, "Йога", emptyList(), emptyList()) }

            assertEquals(emptyList(), fixture.inContext { schedule.slotsOn(groupId, today) })
            assertEquals(0L, versionCount(groupId))
        }

    @Test
    fun `расписание задаётся следом за созданием группы в той же транзакции`() =
        runTest {
            fixture.setUp()
            val hall = fixture.insertHall()
            val groupId = GroupId.new()

            fixture.inContext {
                groups.new(groupId, "Йога", emptyList(), emptyList())
                schedule.setFrom(groupId, today, listOf(NewSlot(DayOfWeek.MONDAY, LocalTime(10, 0), LocalTime(11, 0), hall)))
            }

            assertTrue(fixture.inContext { schedule.slotsOn(groupId, today) }.isNotEmpty())
        }
}
