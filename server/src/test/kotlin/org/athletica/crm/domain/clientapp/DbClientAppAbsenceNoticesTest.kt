package org.athletica.crm.domain.clientapp

import arrow.core.getOrElse
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.attendance.JournalAbsenceNoticeSchema
import org.athletica.crm.api.schemas.attendance.JournalParticipantSchema
import org.athletica.crm.attendance.plusDays
import org.athletica.crm.clientapp.CabinetAttendanceFixture
import org.athletica.crm.core.ClientProfileContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.attendance.AttendancePresence
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.attendance.AttendanceMark
import org.athletica.crm.read.attendance.DbSessionJournalView
import org.athletica.crm.read.clientapp.ClientAppScheduleQuery
import org.athletica.crm.read.clientapp.DbClientAppScheduleView
import org.athletica.crm.storage.asLong
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** DB-тесты «Не придём»: предупреждение в журнале занятия и правила приёма. */
class DbClientAppAbsenceNoticesTest {
    private val fixture = CabinetAttendanceFixture()
    private val attendance = fixture.attendance
    private val notices = DbClientAppAbsenceNotices()

    /** Занятие тестов — вчера в 10:00 по UTC: отметки сотрудника уже доступны. */
    private val day = attendance.yesterday

    /** 09:00 дня занятия — до начала. */
    private val before = fixture.at(day, LocalTime(9, 0))

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    /** Подключённый клиент Иван в группе фикстуры. */
    private suspend fun ivan(): ClientId = fixture.client().also { attendance.enroll(it, day.plusDays(-30)) }

    /** Участник [clientId] в журнале занятия [sessionId] глазами сотрудника. */
    private suspend fun participant(
        sessionId: SessionId,
        clientId: ClientId,
    ): JournalParticipantSchema = attendance.inContext { DbSessionJournalView().journal(sessionId) }.participants.single { it.clientId == clientId }

    /** Число предупреждений в базе. */
    private suspend fun noticeCount(): Long = orThrow { TestPostgres.db.sql("SELECT count(*) AS n FROM client_app_absence_notices").firstOrNull { it.asLong("n") } ?: 0 }

    @Test
    fun `предупреждение видно в журнале с номером и временем, отметка не меняется`() =
        runTest {
            fixture.setUp()
            val ivan = ivan()
            val session = attendance.insertSession(date = day)

            fixture.asMom(ivan) { notices.notify(session, before) }.getOrElse { error(it.toString()) }

            val row = participant(session, ivan)
            assertEquals(JournalAbsenceNoticeSchema("79031234567", before), row.absenceNotice)
            assertNull(row.presence)
            val week = fixture.asMom(ivan) { DbClientAppScheduleView().schedule(ClientAppScheduleQuery(day, before)) }.getOrElse { error(it.toString()) }
            assertEquals(before, week.sessions.single().absenceNoticeAt)
        }

    @Test
    fun `отмена до начала убирает предупреждение и оставляет его в истории, повтор создаёт новое`() =
        runTest {
            fixture.setUp()
            val ivan = ivan()
            val session = attendance.insertSession(date = day)
            fixture.asMom(ivan) { notices.notify(session, before) }.getOrElse { error(it.toString()) }

            fixture.asMom(ivan) { notices.cancel(session, before) }.getOrElse { error(it.toString()) }

            assertNull(participant(session, ivan).absenceNotice)
            assertEquals(1, noticeCount())

            val again = fixture.at(day, LocalTime(9, 30))
            fixture.asMom(ivan) { notices.notify(session, again) }.getOrElse { error(it.toString()) }

            assertEquals(again, participant(session, ivan).absenceNotice?.createdAt)
            assertEquals(2, noticeCount())
        }

    @Test
    fun `клиент пришёл несмотря на предупреждение — отметка сохраняется, предупреждение видно`() =
        runTest {
            fixture.setUp()
            val ivan = ivan()
            val session = attendance.insertSession(date = day)
            fixture.asMom(ivan) { notices.notify(session, before) }.getOrElse { error(it.toString()) }

            attendance.inContext { attendance.journals.byId(session).mark(ivan, AttendanceMark.Present()) }

            val row = participant(session, ivan)
            assertEquals(AttendancePresence.PRESENT, row.presence)
            assertEquals(before, row.absenceNotice?.createdAt)
        }

    @Test
    fun `отменённое и начавшееся занятие — CLIENT_APP_ABSENCE_NOTICE_CLOSED`() =
        runTest {
            fixture.setUp()
            val ivan = ivan()
            val cancelled = attendance.insertSession(date = day, status = "cancelled")
            val session = attendance.insertSession(date = day.plusDays(1))
            val started = fixture.at(day.plusDays(1), LocalTime(10, 5))

            assertEquals("CLIENT_APP_ABSENCE_NOTICE_CLOSED", attendance.errorCode(fixture.asMom(ivan) { notices.notify(cancelled, before) }))
            assertEquals("CLIENT_APP_ABSENCE_NOTICE_CLOSED", attendance.errorCode(fixture.asMom(ivan) { notices.notify(session, started) }))
            assertEquals("CLIENT_APP_ABSENCE_NOTICE_CLOSED", attendance.errorCode(fixture.asMom(ivan) { notices.cancel(session, started) }))
            assertEquals(0, noticeCount())
        }

    @Test
    fun `занятие, где клиента нет в составе, — SESSION_NOT_FOUND`() =
        runTest {
            fixture.setUp()
            val ivan = ivan()
            val other = attendance.schedule.insertGroup("Чужая")
            val session = attendance.insertSession(date = day, group = other)

            assertEquals("SESSION_NOT_FOUND", attendance.errorCode(fixture.asMom(ivan) { notices.notify(session, before) }))
            assertEquals(0, noticeCount())
        }

    @Test
    fun `подмена идентификатора в действии — CLIENT_NOT_FOUND, предупреждение не создаётся`() =
        runTest {
            fixture.setUp()
            ivan()
            val stranger = attendance.insertClient("Чужой")
            attendance.enroll(stranger, day.plusDays(-30))
            val session = attendance.insertSession(date = day)
            val forged = ClientProfileContext(fixture.account.accountId, fixture.account.sessionId, fixture.orgId, stranger, Currency.RUB, Lang.RU)

            val result = attendance.runInContext { context(forged) { notices.notify(session, before) } }

            assertEquals("CLIENT_NOT_FOUND", attendance.errorCode(result))
            assertEquals(0, noticeCount())
        }
}
