package org.athletica.crm.schedule

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import org.athletica.crm.TestPostgres
import org.athletica.crm.attendance.AttendanceFixture
import org.athletica.crm.attendance.plusDays
import org.athletica.crm.core.DayOfWeek
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.entityids.SlotId
import org.athletica.crm.core.entityids.toHallId
import org.athletica.crm.core.sessions.SessionStatus
import org.athletica.crm.domain.attendance.AttendanceMark
import org.athletica.crm.domain.sessions.DbScheduleSync
import org.athletica.crm.storage.asLocalTime
import org.athletica.crm.storage.asUuid
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Сверка расписания не трогает занятия, в журнале которых есть строки:
 * проставленная отметка или разовый участник делают занятие изменённым человеком.
 */
class ScheduleSyncAttendanceTest {
    private val fixture = AttendanceFixture()
    private val sync = DbScheduleSync()

    /** Ближайший понедельник строго после сегодняшнего дня. */
    private val nextMonday = generateSequence(fixture.today.plusDays(1)) { it.plusDays(1) }.first { it.dayOfWeek.name == "MONDAY" }

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    private suspend fun sync() = fixture.inContext { sync.sync() }

    /** Слот по понедельникам в 10:00–11:00 с сегодняшнего дня и занятие ближайшего понедельника. */
    private suspend fun slotWithSession(): Pair<SlotId, SessionId> {
        val slot =
            fixture.schedule.insertSlot(fixture.groupId, fixture.hallId, DayOfWeek.MONDAY, LocalTime(10, 0), LocalTime(11, 0), fixture.today)
        sync()
        val session =
            TestPostgres.db
                .sql("SELECT id FROM sessions WHERE group_id = :g AND date = :d")
                .bind("g", fixture.groupId)
                .bind("d", nextMonday)
                .firstOrNull { SessionId(it.asUuid("id")) }
        return slot to assertNotNull(session)
    }

    @Test
    fun `занятие с отметкой переживает удаление слота`() =
        runTest {
            fixture.setUp()
            val client = fixture.insertClient("Аня")
            fixture.enroll(client, fixture.today.plusDays(-10))
            val (slot, session) = slotWithSession()
            fixture.inContext { fixture.journals.byId(session).mark(client, AttendanceMark.Present()) }

            TestPostgres.db
                .sql("UPDATE schedule_slots SET validity = daterange(lower(validity), :to::date) WHERE id = :id")
                .bind("to", nextMonday)
                .bind("id", slot)
                .execute()
            sync()

            val journal = fixture.journal(session)
            assertEquals(SessionStatus.SCHEDULED, journal.status)
            assertEquals(AttendanceMark.Present(), journal.participants.single { it.clientId == client }.mark)
        }

    @Test
    fun `занятие с разовым участником не перезаписывается при смене свойств слота`() =
        runTest {
            fixture.setUp()
            val guest = fixture.insertClient("Гость")
            val otherHall = fixture.schedule.insertHall("Другой")
            val (slot, session) = slotWithSession()
            fixture.inContext { fixture.journals.byId(session).addOneTime(guest) }

            TestPostgres.db
                .sql("UPDATE schedule_slots SET hall_id = :hall, end_time = '12:00'::time WHERE id = :id")
                .bind("hall", otherHall)
                .bind("id", slot)
                .execute()
            sync()

            val (hall, endTime) =
                assertNotNull(
                    TestPostgres.db
                        .sql("SELECT hall_id, end_time FROM sessions WHERE id = :id")
                        .bind("id", session)
                        .firstOrNull { it.asUuid("hall_id").toHallId() to it.asLocalTime("end_time") },
                )
            assertEquals(fixture.hallId, hall)
            assertEquals(LocalTime(11, 0), endTime)
            assertEquals(listOf(guest), fixture.journal(session).participants.map { it.clientId })
        }
}
