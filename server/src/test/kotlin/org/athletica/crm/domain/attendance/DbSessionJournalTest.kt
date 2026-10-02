package org.athletica.crm.domain.attendance

import arrow.core.getOrElse
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.attendance.AttendanceFixture
import org.athletica.crm.attendance.plusDays
import org.athletica.crm.core.attendance.AttendanceLabelScope
import org.athletica.crm.core.attendance.AttendancePresence
import org.athletica.crm.core.attendance.ParticipationKind
import org.athletica.crm.core.entityids.AttendanceLabelId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.sessions.SessionStatus
import org.athletica.crm.domain.events.AttendanceChanged
import org.athletica.crm.domain.events.AttendanceMarkSnapshot
import org.athletica.crm.domain.events.AttendanceMarked
import org.athletica.crm.domain.events.CompletedParticipant
import org.athletica.crm.domain.events.SessionCancelled
import org.athletica.crm.domain.events.SessionCompleted
import org.athletica.crm.storage.asLong
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * DB-тесты журнала посещаемости: состав занятия, отметки, разовые участники,
 * закрытие журнала, переходы занятия и публикация событий.
 */
class DbSessionJournalTest {
    private val fixture = AttendanceFixture()

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    private fun absent(labels: Set<AttendanceLabelId>) = AttendanceMark.Absent.from(labels).getOrElse { error(it) }

    private suspend fun mark(sessionId: SessionId, clientId: ClientId, mark: AttendanceMark) = fixture.inContext { fixture.journals.byId(sessionId).mark(clientId, mark) }

    private suspend fun tryMark(sessionId: SessionId, clientId: ClientId, mark: AttendanceMark) = fixture.runInContext { fixture.journals.byId(sessionId).mark(clientId, mark) }

    private suspend fun complete(sessionId: SessionId) = fixture.runInContext { fixture.sessions.byId(sessionId).complete() }

    private suspend fun cancel(sessionId: SessionId) = fixture.runInContext { fixture.sessions.byId(sessionId).cancel() }

    private suspend fun participants(sessionId: SessionId) = fixture.journal(sessionId).participants.associateBy { it.clientId }

    private suspend fun status(sessionId: SessionId) = fixture.journal(sessionId).status

    /** Занятие вчера с тремя постоянными участниками группы. */
    private suspend fun sessionWithThree(): Pair<SessionId, List<ClientId>> {
        val clients = listOf("Аня", "Борис", "Вера").map { fixture.insertClient(it) }
        clients.forEach { fixture.enroll(it, fixture.yesterday.plusDays(-30)) }
        return fixture.insertSession() to clients
    }

    @Test
    fun `постоянные участники подставляются из группы не отмеченными`() =
        runTest {
            fixture.setUp()
            val (session, clients) = sessionWithThree()

            val roster = participants(session)

            assertEquals(clients.toSet(), roster.keys)
            assertTrue(roster.values.all { it.kind == ParticipationKind.REGULAR && it.mark == AttendanceMark.Unmarked })
        }

    @Test
    fun `вышедший до занятия и записанный после занятия не попадают в состав`() =
        runTest {
            fixture.setUp()
            val stays = fixture.insertClient("Остался")
            val left = fixture.insertClient("Ушёл")
            val late = fixture.insertClient("Пришёл позже")
            fixture.enroll(stays, fixture.yesterday.plusDays(-30))
            fixture.enroll(left, fixture.yesterday.plusDays(-30), leftAt = fixture.yesterday.plusDays(-1))
            fixture.enroll(late, fixture.today)
            val session = fixture.insertSession()

            assertEquals(setOf(stays), participants(session).keys)
        }

    @Test
    fun `смешанный состав содержит постоянных и разовых с видом участия`() =
        runTest {
            fixture.setUp()
            val (session, clients) = sessionWithThree()
            val guest = fixture.insertClient("Гость")

            fixture.inContext { fixture.journals.byId(session).addOneTime(guest) }

            val roster = participants(session)
            assertEquals(clients.toSet() + guest, roster.keys)
            assertEquals(ParticipationKind.ONE_TIME, roster.getValue(guest).kind)
            assertEquals(AttendanceMark.Unmarked, roster.getValue(guest).mark)
            assertEquals(ParticipationKind.REGULAR, roster.getValue(clients.first()).kind)
        }

    @Test
    fun `первая отметка создаёт строку журнала с метками`() =
        runTest {
            fixture.setUp()
            val (session, clients) = sessionWithThree()
            val late = fixture.insertLabel("Опоздал", AttendanceLabelScope.PRESENT)
            val noUniform = fixture.insertLabel("Без формы", AttendanceLabelScope.ANY)

            mark(session, clients[0], AttendanceMark.Present(setOf(late, noUniform)))

            assertEquals(AttendanceMark.Present(setOf(late, noUniform)), participants(session).getValue(clients[0]).mark)
            val rows =
                TestPostgres.db
                    .sql("SELECT COUNT(*) AS cnt FROM session_attendance WHERE session_id = :s")
                    .bind("s", session)
                    .firstOrNull { it.asLong("cnt") }
            assertEquals(1L, rows)
        }

    @Test
    fun `переотметка в открытом журнале заменяет прежнюю`() =
        runTest {
            fixture.setUp()
            val (session, clients) = sessionWithThree()
            val sick = fixture.insertLabel("Болеет", AttendanceLabelScope.ABSENT)
            mark(session, clients[0], AttendanceMark.Present())

            mark(session, clients[0], absent(setOf(sick)))

            assertEquals(absent(setOf(sick)), participants(session).getValue(clients[0]).mark)
        }

    @Test
    fun `метка, не применимая к состоянию, отклоняется`() =
        runTest {
            fixture.setUp()
            val (session, clients) = sessionWithThree()
            val sick = fixture.insertLabel("Болеет", AttendanceLabelScope.ABSENT)

            val result = tryMark(session, clients[0], AttendanceMark.Present(setOf(sick)))

            assertEquals("ATTENDANCE_LABEL_NOT_APPLICABLE", fixture.errorCode(result))
            assertEquals(AttendanceMark.Unmarked, participants(session).getValue(clients[0]).mark)
        }

    @Test
    fun `архивная метка не назначается новой отметке, но остаётся на проставленной`() =
        runTest {
            fixture.setUp()
            val (session, clients) = sessionWithThree()
            val sick = fixture.insertLabel("Болеет", AttendanceLabelScope.ABSENT)
            val truant = fixture.insertLabel("Прогул", AttendanceLabelScope.ABSENT)
            mark(session, clients[0], absent(setOf(sick)))
            TestPostgres.db.sql("UPDATE attendance_labels SET archived_at = now() WHERE id = :id").bind("id", sick).execute()

            val result = tryMark(session, clients[1], absent(setOf(sick)))
            mark(session, clients[0], absent(setOf(sick, truant)))

            assertEquals("ATTENDANCE_LABEL_ARCHIVED", fixture.errorCode(result))
            assertEquals(absent(setOf(sick, truant)), participants(session).getValue(clients[0]).mark)
        }

    @Test
    fun `отметка клиента вне состава отклоняется`() =
        runTest {
            fixture.setUp()
            val (session, _) = sessionWithThree()
            val stranger = fixture.insertClient("Чужой")

            val result = tryMark(session, stranger, AttendanceMark.Present())

            assertEquals("ATTENDANCE_PARTICIPANT_NOT_FOUND", fixture.errorCode(result))
        }

    @Test
    fun `повторное добавление клиента из состава отклоняется`() =
        runTest {
            fixture.setUp()
            val (session, clients) = sessionWithThree()
            val guest = fixture.insertClient("Гость")
            fixture.inContext { fixture.journals.byId(session).addOneTime(guest) }

            val regular = fixture.runInContext { fixture.journals.byId(session).addOneTime(clients[0]) }
            val again = fixture.runInContext { fixture.journals.byId(session).addOneTime(guest) }

            assertEquals("ATTENDANCE_PARTICIPANT_ALREADY_EXISTS", fixture.errorCode(regular))
            assertEquals("ATTENDANCE_PARTICIPANT_ALREADY_EXISTS", fixture.errorCode(again))
            assertEquals(4, participants(session).size)
        }

    @Test
    fun `разового участника нельзя добавить в проведённое или отменённое занятие`() =
        runTest {
            fixture.setUp()
            val guest = fixture.insertClient("Гость")
            val completed = fixture.insertSession(status = "completed")
            val cancelled = fixture.insertSession(date = fixture.today, status = "cancelled")

            val toCompleted = fixture.runInContext { fixture.journals.byId(completed).addOneTime(guest) }
            val toCancelled = fixture.runInContext { fixture.journals.byId(cancelled).addOneTime(guest) }

            assertEquals("ATTENDANCE_JOURNAL_CLOSED", fixture.errorCode(toCompleted))
            assertEquals("ATTENDANCE_SESSION_CANCELLED", fixture.errorCode(toCancelled))
        }

    @Test
    fun `разовый участник удаляется вместе с отметкой`() =
        runTest {
            fixture.setUp()
            val (session, clients) = sessionWithThree()
            val guest = fixture.insertClient("Гость")
            fixture.inContext { fixture.journals.byId(session).addOneTime(guest) }
            mark(session, guest, AttendanceMark.Present())

            fixture.inContext { fixture.journals.byId(session).removeOneTime(guest) }

            assertEquals(clients.toSet(), participants(session).keys)
        }

    @Test
    fun `постоянного участника удалить нельзя`() =
        runTest {
            fixture.setUp()
            val (session, clients) = sessionWithThree()

            val result = fixture.runInContext { fixture.journals.byId(session).removeOneTime(clients[0]) }

            assertEquals("ATTENDANCE_CANNOT_REMOVE_REGULAR", fixture.errorCode(result))
            assertEquals(clients.toSet(), participants(session).keys)
        }

    @Test
    fun `массовая отметка трогает только неотмеченных`() =
        runTest {
            fixture.setUp()
            val (session, clients) = sessionWithThree()
            val truant = fixture.insertLabel("Прогул", AttendanceLabelScope.ABSENT)
            mark(session, clients[0], AttendanceMark.Present())

            fixture.inContext { fixture.journals.byId(session).markRemainingAbsent(truant) }

            val roster = participants(session)
            assertEquals(AttendanceMark.Present(), roster.getValue(clients[0]).mark)
            assertEquals(absent(setOf(truant)), roster.getValue(clients[1]).mark)
            assertEquals(absent(setOf(truant)), roster.getValue(clients[2]).mark)
        }

    @Test
    fun `массовая отметка с меткой присутствия отклоняется`() =
        runTest {
            fixture.setUp()
            val (session, _) = sessionWithThree()
            val late = fixture.insertLabel("Опоздал", AttendanceLabelScope.PRESENT)

            val result = fixture.runInContext { fixture.journals.byId(session).markRemainingAbsent(late) }

            assertEquals("ATTENDANCE_LABEL_NOT_APPLICABLE", fixture.errorCode(result))
        }

    @Test
    fun `отметка в закрытом занятии правится, занятие остаётся закрытым`() =
        runTest {
            fixture.setUp()
            val (session, clients) = sessionWithThree()
            val truant = fixture.insertLabel("Прогул", AttendanceLabelScope.ABSENT)
            val sick = fixture.insertLabel("Болеет", AttendanceLabelScope.ABSENT)
            fixture.inContext { fixture.journals.byId(session).markRemainingAbsent(truant) }
            complete(session).getOrElse { error(it) }

            mark(session, clients[0], absent(setOf(sick)))

            assertEquals(SessionStatus.COMPLETED, status(session))
            assertEquals(absent(setOf(sick)), participants(session).getValue(clients[0]).mark)
        }

    @Test
    fun `снять отметку в закрытом занятии нельзя`() =
        runTest {
            fixture.setUp()
            val (session, clients) = sessionWithThree()
            val truant = fixture.insertLabel("Прогул", AttendanceLabelScope.ABSENT)
            fixture.inContext { fixture.journals.byId(session).markRemainingAbsent(truant) }
            complete(session).getOrElse { error(it) }

            val result = tryMark(session, clients[0], AttendanceMark.Unmarked)

            assertEquals("ATTENDANCE_CANNOT_UNMARK_COMPLETED", fixture.errorCode(result))
        }

    @Test
    fun `закрытие отклоняется, пока есть неотмеченные`() =
        runTest {
            fixture.setUp()
            val (session, clients) = sessionWithThree()
            mark(session, clients[0], AttendanceMark.Present())

            val result = complete(session)

            assertEquals("SESSION_JOURNAL_INCOMPLETE", fixture.errorCode(result))
            assertEquals(SessionStatus.SCHEDULED, status(session))
        }

    @Test
    fun `пустое занятие закрывается`() =
        runTest {
            fixture.setUp()
            val session = fixture.insertSession()

            complete(session).getOrElse { error(it) }

            assertEquals(SessionStatus.COMPLETED, status(session))
        }

    @Test
    fun `занятие, которое ещё не началось, закрыть нельзя`() =
        runTest {
            fixture.setUp()
            val session = fixture.insertSession(date = fixture.today.plusDays(1))

            val result = complete(session)

            assertEquals("SESSION_NOT_STARTED", fixture.errorCode(result))
        }

    @Test
    fun `проведённое занятие отменить нельзя`() =
        runTest {
            fixture.setUp()
            val session = fixture.insertSession()
            complete(session).getOrElse { error(it) }

            val result = cancel(session)

            assertEquals("SESSION_CANNOT_CANCEL", fixture.errorCode(result))
            assertEquals(SessionStatus.COMPLETED, status(session))
        }

    @Test
    fun `в отменённом занятии отмечать и менять состав нельзя`() =
        runTest {
            fixture.setUp()
            val (session, clients) = sessionWithThree()
            mark(session, clients[0], AttendanceMark.Present())
            cancel(session).getOrElse { error(it) }
            val guest = fixture.insertClient("Гость")

            val marking = tryMark(session, clients[0], AttendanceMark.Present(setOf()))
            val remark = tryMark(session, clients[0], AttendanceMark.Unmarked)
            val adding = fixture.runInContext { fixture.journals.byId(session).addOneTime(guest) }

            assertEquals("ATTENDANCE_SESSION_CANCELLED", fixture.errorCode(marking))
            assertEquals("ATTENDANCE_SESSION_CANCELLED", fixture.errorCode(remark))
            assertEquals("ATTENDANCE_SESSION_CANCELLED", fixture.errorCode(adding))
        }

    @Test
    fun `состав закрытого занятия не меняется от записи в группу задним числом`() =
        runTest {
            fixture.setUp()
            val (session, clients) = sessionWithThree()
            val truant = fixture.insertLabel("Прогул", AttendanceLabelScope.ABSENT)
            fixture.inContext { fixture.journals.byId(session).markRemainingAbsent(truant) }
            complete(session).getOrElse { error(it) }
            val newcomer = fixture.insertClient("Новичок")
            fixture.enroll(newcomer, fixture.yesterday.plusDays(-10))

            assertEquals(clients.toSet(), participants(session).keys)
        }

    @Test
    fun `ушедший из группы после закрытия остаётся в составе с отметкой`() =
        runTest {
            fixture.setUp()
            val (session, clients) = sessionWithThree()
            val truant = fixture.insertLabel("Прогул", AttendanceLabelScope.ABSENT)
            fixture.inContext { fixture.journals.byId(session).markRemainingAbsent(truant) }
            complete(session).getOrElse { error(it) }
            TestPostgres.db
                .sql("UPDATE enrollments SET left_at = :d::date::timestamptz WHERE client_id = :c")
                .bind("d", fixture.yesterday.plusDays(-5))
                .bind("c", clients[0])
                .execute()

            assertEquals(absent(setOf(truant)), participants(session).getValue(clients[0]).mark)
        }

    @Test
    fun `отметка, её правка и проведение публикуют события`() =
        runTest {
            fixture.setUp()
            val (session, clients) = sessionWithThree()
            val sick = fixture.insertLabel("Болеет", AttendanceLabelScope.ABSENT)
            val truant = fixture.insertLabel("Прогул", AttendanceLabelScope.ABSENT)

            mark(session, clients[0], AttendanceMark.Present())
            mark(session, clients[0], absent(setOf(sick)))

            val events = fixture.events()
            assertEquals(2, events.size)
            val marked = assertIs<AttendanceMarked>(events[0])
            assertEquals(AttendanceMarkSnapshot(AttendancePresence.PRESENT, emptyList()), marked.mark)
            assertEquals(fixture.schedule.actorId, marked.markedBy)
            val changed = assertIs<AttendanceChanged>(events[1])
            assertEquals(AttendanceMarkSnapshot(AttendancePresence.PRESENT, emptyList()), changed.previous)
            assertEquals(AttendanceMarkSnapshot(AttendancePresence.ABSENT, listOf(sick)), changed.current)
            assertEquals(clients[0], changed.clientId)
        }

    @Test
    fun `сохранение той же отметки события не публикует`() =
        runTest {
            fixture.setUp()
            val (session, clients) = sessionWithThree()
            val sick = fixture.insertLabel("Болеет", AttendanceLabelScope.ABSENT)
            mark(session, clients[0], absent(setOf(sick)))

            mark(session, clients[0], absent(setOf(sick)))

            assertEquals(1, fixture.events().size)
        }

    @Test
    fun `проведение после массовой отметки публикует ровно одно событие с итоговым составом`() =
        runTest {
            fixture.setUp()
            val (session, clients) = sessionWithThree()
            val truant = fixture.insertLabel("Прогул", AttendanceLabelScope.ABSENT)
            mark(session, clients[0], AttendanceMark.Present())
            fixture.inContext { fixture.journals.byId(session).markRemainingAbsent(truant) }

            complete(session).getOrElse { error(it) }
            mark(session, clients[1], AttendanceMark.Present())

            val events = fixture.events()
            assertEquals(3, events.filterIsInstance<AttendanceMarked>().size)
            assertEquals(1, events.filterIsInstance<AttendanceChanged>().size)
            val completed = events.filterIsInstance<SessionCompleted>().single()
            assertEquals(session, completed.sessionId)
            assertEquals(
                setOf(
                    CompletedParticipant(clients[0], ParticipationKind.REGULAR, AttendancePresence.PRESENT, emptyList()),
                    CompletedParticipant(clients[1], ParticipationKind.REGULAR, AttendancePresence.ABSENT, listOf(truant)),
                    CompletedParticipant(clients[2], ParticipationKind.REGULAR, AttendancePresence.ABSENT, listOf(truant)),
                ),
                completed.participants.toSet(),
            )
        }

    @Test
    fun `отмена занятия публикует событие`() =
        runTest {
            fixture.setUp()
            val session = fixture.insertSession(date = fixture.today.plusDays(3))

            cancel(session).getOrElse { error(it) }

            val event = assertIs<SessionCancelled>(fixture.events().single())
            assertEquals(session, event.sessionId)
            assertEquals(fixture.groupId, event.groupId)
        }
}
