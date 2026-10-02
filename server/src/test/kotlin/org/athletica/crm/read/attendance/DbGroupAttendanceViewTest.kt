package org.athletica.crm.read.attendance

import arrow.core.getOrElse
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.attendance.AttendanceFixture
import org.athletica.crm.attendance.plusDays
import org.athletica.crm.core.attendance.AttendanceLabelScope
import org.athletica.crm.core.attendance.AttendancePresence
import org.athletica.crm.domain.attendance.AttendanceMark
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals

/** DB-тесты сводки по группе: учитываются только проведённые занятия периода. */
class DbGroupAttendanceViewTest {
    private val fixture = AttendanceFixture()
    private val view = DbGroupAttendanceView()

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    @Test
    fun `незакрытые и отменённые занятия в сводку не попадают`() =
        runTest {
            fixture.setUp()
            val anna = fixture.insertClient("Аня")
            val boris = fixture.insertClient("Борис")
            listOf(anna, boris).forEach { fixture.enroll(it, fixture.today.plusDays(-30)) }
            val sick = fixture.insertLabel("Болеет", AttendanceLabelScope.ABSENT)
            val absent = AttendanceMark.Absent.from(setOf(sick)).getOrElse { error(it) }
            val closed = fixture.insertSession(date = fixture.today.plusDays(-3))
            val open = fixture.insertSession(date = fixture.today.plusDays(-2))
            val cancelled = fixture.insertSession(date = fixture.today.plusDays(-1))
            fixture.inContext {
                listOf(closed, open, cancelled).forEach { session ->
                    val journal = fixture.journals.byId(session)
                    journal.mark(anna, AttendanceMark.Present())
                    journal.mark(boris, absent)
                }
                fixture.sessions.byId(closed).complete()
                fixture.sessions.byId(cancelled).cancel()
            }

            val summary =
                fixture.inContext {
                    view.summary(GroupAttendanceQuery(fixture.groupId, fixture.today.plusDays(-7), fixture.today))
                }

            assertEquals(listOf(closed), summary.sessions.map { it.id })
            assertEquals(listOf("Аня", "Борис"), summary.participants.map { it.name })
            assertEquals(listOf(1 to 0, 0 to 1), summary.participants.map { it.presentCount to it.absentCount })
            val borisMark = summary.participants[1].marks.single()
            assertEquals(closed, borisMark.sessionId)
            assertEquals(AttendancePresence.ABSENT, borisMark.presence)
            assertEquals(listOf("Болеет"), borisMark.labels.map { it.name })
        }

    @Test
    fun `занятия вне периода не учитываются`() =
        runTest {
            fixture.setUp()
            val session = fixture.insertSession(date = fixture.today.plusDays(-20), status = "completed")

            val summary =
                fixture.inContext {
                    view.summary(GroupAttendanceQuery(fixture.groupId, fixture.today.plusDays(-7), fixture.today))
                }

            assertEquals(emptyList(), summary.sessions.map { it.id }.filter { it == session })
            assertEquals(emptyList(), summary.participants)
        }
}
