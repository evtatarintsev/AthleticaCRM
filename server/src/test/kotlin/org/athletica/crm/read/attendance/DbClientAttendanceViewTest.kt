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

/** DB-тесты посещаемости клиента: отметки по проведённым занятиям всех групп за период. */
class DbClientAttendanceViewTest {
    private val fixture = AttendanceFixture()
    private val view = DbClientAttendanceView()

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    @Test
    fun `отметки клиента по всем группам за период без отменённых занятий`() =
        runTest {
            fixture.setUp()
            val secondGroup = fixture.schedule.insertGroup("Вторая")
            val anna = fixture.insertClient("Аня")
            fixture.enroll(anna, fixture.today.plusDays(-60))
            fixture.enroll(anna, fixture.today.plusDays(-60), group = secondGroup)
            val late = fixture.insertLabel("Опоздал", AttendanceLabelScope.PRESENT)
            val sick = fixture.insertLabel("Болеет", AttendanceLabelScope.ABSENT)
            val first = fixture.insertSession(date = fixture.today.plusDays(-5))
            val second = fixture.insertSession(date = fixture.today.plusDays(-3), group = secondGroup)
            val cancelled = fixture.insertSession(date = fixture.today.plusDays(-2))
            val outside = fixture.insertSession(date = fixture.today.plusDays(-40))
            fixture.inContext {
                fixture.journals.byId(first).mark(anna, AttendanceMark.Present(setOf(late)))
                fixture.journals.byId(second).mark(anna, AttendanceMark.Absent.from(setOf(sick)).getOrElse { error(it) })
                fixture.journals.byId(cancelled).mark(anna, AttendanceMark.Present())
                fixture.journals.byId(outside).mark(anna, AttendanceMark.Present())
                listOf(first, second, outside).forEach { fixture.sessions.byId(it).complete() }
                fixture.sessions.byId(cancelled).cancel()
            }

            val items =
                fixture.inContext {
                    view.list(ClientAttendanceQuery(anna, fixture.today.plusDays(-30), fixture.today))
                }.items

            assertEquals(listOf(first, second), items.map { it.sessionId })
            assertEquals(listOf(fixture.groupId, secondGroup), items.map { it.group.id })
            assertEquals(listOf(AttendancePresence.PRESENT, AttendancePresence.ABSENT), items.map { it.presence })
            assertEquals(listOf(listOf("Опоздал"), listOf("Болеет")), items.map { it.labels.map { label -> label.name } })
        }
}
