package org.athletica.crm.read.clientapp

import arrow.core.getOrElse
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.clientapp.ClientAppMovedFromSchema
import org.athletica.crm.api.schemas.clientapp.ClientAppScheduleResponse
import org.athletica.crm.attendance.plusDays
import org.athletica.crm.clientapp.CabinetAttendanceFixture
import org.athletica.crm.core.DayOfWeek
import org.athletica.crm.core.clientapp.ClientAppAccessStatus
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.core.sessions.SessionStatus
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** DB-тесты проекции расписания кабинета клиента. */
class ClientAppScheduleViewTest {
    private val fixture = CabinetAttendanceFixture()
    private val attendance = fixture.attendance
    private val view = DbClientAppScheduleView()

    /** Понедельник недели теста; «сейчас» — 09:00 этого дня. */
    private val monday = LocalDate(2030, 1, 7)

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    /** Расписание клиента [clientId] за неделю с [weekOf] на 09:00 понедельника. */
    private suspend fun schedule(
        clientId: ClientId,
        weekOf: LocalDate? = monday,
    ): ClientAppScheduleResponse =
        fixture.asMom(clientId) { view.schedule(ClientAppScheduleQuery(weekOf, fixture.at(monday, LocalTime(9, 0)))) }
            .getOrElse { error(it.toString()) }

    /** Разовое участие клиента [clientId] в занятии [sessionId]. */
    private suspend fun oneTime(
        sessionId: SessionId,
        clientId: ClientId,
    ) {
        orThrow {
            TestPostgres.db.sql("INSERT INTO session_attendance (session_id, client_id, kind) VALUES (:s, :c, 'one_time')")
                .bind("s", sessionId).bind("c", clientId).execute()
        }
    }

    @Test
    fun `неделя — занятия своей группы и разовое участие, чужой группы нет`() =
        runTest {
            fixture.setUp()
            val ivan = fixture.client()
            attendance.enroll(ivan, monday.plusDays(-30))
            val coach = attendance.schedule.insertEmployee("Сергей")
            val own = attendance.insertSession(date = monday)
            attendance.schedule.linkSessionEmployee(own, coach)
            val other = attendance.schedule.insertGroup("Чужая")
            attendance.insertSession(date = monday.plusDays(1), group = other)
            val guest = attendance.insertSession(date = monday.plusDays(2), group = other)
            oneTime(guest, ivan)
            val nextWeek = attendance.insertSession(date = monday.plusDays(7))

            val week = schedule(ivan)

            assertEquals(monday, week.weekStart)
            assertEquals(monday, week.today)
            assertEquals(listOf(own, guest), week.sessions.map { it.sessionId })
            val first = week.sessions.first()
            assertEquals(listOf("Сергей"), first.coaches)
            assertEquals(LocalTime(10, 0), first.startTime)
            assertEquals(SessionStatus.SCHEDULED, first.status)
            assertEquals(false, first.hasStarted)
            assertNull(first.movedFrom)
            assertNull(first.absenceNoticeAt)
            assertEquals("Чужая", week.sessions[1].groupName)
            assertEquals(own, week.next?.sessionId)
            assertEquals(listOf(nextWeek), schedule(ivan, weekOf = monday.plusDays(9)).sessions.map { it.sessionId })
        }

    @Test
    fun `перенос с прежним временем и отменённое занятие`() =
        runTest {
            fixture.setUp()
            val ivan = fixture.client()
            attendance.enroll(ivan, monday.plusDays(-30))
            val tuesday = monday.plusDays(1)
            val slot = attendance.schedule.insertSlot(attendance.groupId, attendance.hallId, DayOfWeek.TUESDAY, LocalTime(10, 0), LocalTime(11, 0), monday.plusDays(-30))
            val moved =
                attendance.schedule.insertSession(
                    attendance.groupId, attendance.hallId, tuesday, LocalTime(11, 0), LocalTime(12, 0), originSlotId = slot, isRescheduled = true,
                )
            val cancelled = attendance.insertSession(date = monday.plusDays(3), status = "cancelled")

            val week = schedule(ivan)

            assertEquals(listOf(moved, cancelled), week.sessions.map { it.sessionId })
            assertEquals(ClientAppMovedFromSchema(tuesday, LocalTime(10, 0)), week.sessions[0].movedFrom)
            assertEquals(SessionStatus.CANCELLED, week.sessions[1].status)
            assertEquals(moved, week.next?.sessionId)
        }

    @Test
    fun `без активной записи на дату и без разового участия занятий нет`() =
        runTest {
            fixture.setUp()
            val ivan = fixture.client()
            attendance.enroll(ivan, monday.plusDays(-30), leftAt = monday.plusDays(-1))
            attendance.insertSession(date = monday)

            val week = schedule(ivan)

            assertEquals(emptyList(), week.sessions)
            assertNull(week.next)
        }

    @Test
    fun `без подключённого доступа — CLIENT_NOT_FOUND`() =
        runTest {
            fixture.setUp()
            val ivan = fixture.client(status = ClientAppAccessStatus.INVITED)

            val result = fixture.asMom(ivan) { view.schedule(ClientAppScheduleQuery(null, fixture.at(monday, LocalTime(9, 0)))) }

            assertEquals("CLIENT_NOT_FOUND", attendance.errorCode(result))
        }
}
