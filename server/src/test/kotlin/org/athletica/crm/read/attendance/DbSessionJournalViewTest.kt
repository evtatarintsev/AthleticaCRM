package org.athletica.crm.read.attendance

import arrow.core.getOrElse
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.attendance.AttendanceFixture
import org.athletica.crm.attendance.plusDays
import org.athletica.crm.core.attendance.AttendanceLabelScope
import org.athletica.crm.core.attendance.AttendancePresence
import org.athletica.crm.core.attendance.ParticipationKind
import org.athletica.crm.core.sessions.SessionStatus
import org.athletica.crm.domain.attendance.AttendanceMark
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** DB-тесты проекции журнала занятия: состав с отметками и метками с учётом статуса занятия. */
class DbSessionJournalViewTest {
    private val fixture = AttendanceFixture()
    private val view = DbSessionJournalView()

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    @Test
    fun `журнал открытого занятия — группа и разовые участники с отметками по имени`() =
        runTest {
            fixture.setUp()
            val coach = fixture.schedule.insertEmployee("Сергей")
            val anna = fixture.insertClient("Аня")
            val boris = fixture.insertClient("Борис")
            val guest = fixture.insertClient("Вера")
            fixture.enroll(anna, fixture.yesterday.plusDays(-30))
            fixture.enroll(boris, fixture.yesterday.plusDays(-30))
            val sick = fixture.insertLabel("Болеет", AttendanceLabelScope.ABSENT)
            val session = fixture.insertSession()
            fixture.schedule.linkSessionEmployee(session, coach)
            fixture.inContext {
                val journal = fixture.journals.byId(session)
                journal.addOneTime(guest)
                journal.mark(boris, AttendanceMark.Absent.from(setOf(sick)).getOrElse { error(it) })
            }
            TestPostgres.db.sql("UPDATE attendance_labels SET archived_at = now() WHERE id = :id").bind("id", sick).execute()

            val journal = fixture.inContext { view.journal(session) }

            assertEquals(SessionStatus.SCHEDULED, journal.status)
            assertTrue(journal.hasStarted)
            assertEquals(listOf("Сергей"), journal.coaches.map { it.name })
            assertEquals(listOf("Аня", "Борис", "Вера"), journal.participants.map { it.name })
            assertEquals(
                listOf(ParticipationKind.REGULAR, ParticipationKind.REGULAR, ParticipationKind.ONE_TIME),
                journal.participants.map { it.kind },
            )
            assertEquals(listOf(null, AttendancePresence.ABSENT, null), journal.participants.map { it.presence })
            val label = journal.participants[1].labels.single()
            assertEquals(sick, label.id)
            assertTrue(label.isArchived)
        }

    @Test
    fun `журнал закрытого занятия читается только из строк журнала`() =
        runTest {
            fixture.setUp()
            val anna = fixture.insertClient("Аня")
            fixture.enroll(anna, fixture.yesterday.plusDays(-30))
            val session = fixture.insertSession()
            fixture.inContext { fixture.journals.byId(session).mark(anna, AttendanceMark.Present()) }
            fixture.inContext { fixture.sessions.byId(session).complete() }
            val newcomer = fixture.insertClient("Новичок")
            fixture.enroll(newcomer, fixture.yesterday.plusDays(-10))

            val journal = fixture.inContext { view.journal(session) }

            assertEquals(SessionStatus.COMPLETED, journal.status)
            assertEquals(listOf(anna), journal.participants.map { it.clientId })
        }

    @Test
    fun `будущее занятие ещё не началось`() =
        runTest {
            fixture.setUp()
            val session = fixture.insertSession(date = fixture.today.plusDays(2))

            assertFalse(fixture.inContext { view.journal(session) }.hasStarted)
        }

    @Test
    fun `занятие чужой организации не найдено`() =
        runTest {
            fixture.setUp()
            val session = fixture.insertSession()
            val other = AttendanceFixture()
            other.setUp()

            assertEquals("SESSION_NOT_FOUND", other.errorCode(other.runInContext { view.journal(session) }))
        }
}
