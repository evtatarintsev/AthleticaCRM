package org.athletica.crm.read.clientapp

import arrow.core.getOrElse
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.appJson
import org.athletica.crm.api.schemas.clientapp.ClientAppVisitsResponse
import org.athletica.crm.clientapp.CabinetAttendanceFixture
import org.athletica.crm.core.attendance.AttendanceLabelScope
import org.athletica.crm.core.attendance.AttendancePresence
import org.athletica.crm.core.clientapp.ClientAppAccessStatus
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.storage.asUuid
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.uuid.Uuid

/** DB-тесты проекции истории посещений кабинета клиента. */
class ClientAppVisitsViewTest {
    private val fixture = CabinetAttendanceFixture()
    private val attendance = fixture.attendance
    private val view = DbClientAppVisitsView()

    /** «Сегодня» теста; «сейчас» — 09:00 этого дня. */
    private val today = LocalDate(2030, 1, 15)

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    /** История клиента [clientId] за месяц с [monthOf] на 09:00 «сегодня». */
    private suspend fun visits(
        clientId: ClientId,
        monthOf: LocalDate? = null,
    ): ClientAppVisitsResponse =
        fixture.asMom(clientId) { view.visits(ClientAppVisitsQuery(monthOf, fixture.at(today, LocalTime(9, 0)))) }
            .getOrElse { error(it.toString()) }

    /** Отметка [presence] клиента [clientId] на занятии [sessionId]; возвращает строку журнала. */
    private suspend fun mark(
        sessionId: SessionId,
        clientId: ClientId,
        presence: AttendancePresence?,
    ): Uuid =
        orThrow {
            TestPostgres.db
                .sql(
                    """
                    INSERT INTO session_attendance (session_id, client_id, kind, presence)
                    VALUES (:s, :c, 'regular', CAST(:p AS attendance_presence)) RETURNING id
                    """.trimIndent(),
                )
                .bind("s", sessionId).bind("c", clientId).bind("p", presence?.name?.lowercase())
                .firstOrNull { it.asUuid("id") } ?: error("строка журнала не создана")
        }

    @Test
    fun `месяц — проведённые занятия с отметкой, без неотмеченных, непроведённых и других месяцев`() =
        runTest {
            fixture.setUp()
            val ivan = fixture.client()
            val present = attendance.insertSession(date = LocalDate(2030, 1, 3), status = "completed")
            mark(present, ivan, AttendancePresence.PRESENT)
            val absent = attendance.insertSession(date = LocalDate(2030, 1, 10), status = "completed")
            mark(absent, ivan, AttendancePresence.ABSENT)
            mark(attendance.insertSession(date = LocalDate(2030, 1, 12), status = "completed"), ivan, null)
            mark(attendance.insertSession(date = LocalDate(2030, 1, 20), status = "scheduled"), ivan, null)
            val december = attendance.insertSession(date = LocalDate(2029, 12, 31), status = "completed")
            mark(december, ivan, AttendancePresence.PRESENT)

            val month = visits(ivan)

            assertEquals(LocalDate(2030, 1, 1), month.monthStart)
            assertEquals(listOf(present, absent), month.visits.map { it.sessionId })
            assertEquals(listOf(AttendancePresence.PRESENT, AttendancePresence.ABSENT), month.visits.map { it.presence })
            assertEquals(1, month.presentCount)
            assertEquals(listOf(december), visits(ivan, monthOf = LocalDate(2029, 12, 5)).visits.map { it.sessionId })
        }

    @Test
    fun `метка не отдаётся`() =
        runTest {
            fixture.setUp()
            val ivan = fixture.client()
            val session = attendance.insertSession(date = LocalDate(2030, 1, 10), status = "completed")
            val row = mark(session, ivan, AttendancePresence.ABSENT)
            val label = attendance.insertLabel("болезнь", AttendanceLabelScope.ABSENT)
            orThrow {
                TestPostgres.db.sql("INSERT INTO session_attendance_labels (attendance_id, label_id) VALUES (:a, :l)")
                    .bind("a", row).bind("l", label).execute()
            }

            val month = visits(ivan)

            assertEquals(AttendancePresence.ABSENT, month.visits.single().presence)
            assertFalse(appJson.encodeToString(month).contains("болезнь"))
        }

    @Test
    fun `отметки другого клиента не видны`() =
        runTest {
            fixture.setUp()
            val ivan = fixture.client()
            val maria = fixture.client(name = "Мария")
            mark(attendance.insertSession(date = LocalDate(2030, 1, 10), status = "completed"), maria, AttendancePresence.PRESENT)

            assertEquals(emptyList(), visits(ivan).visits)
        }

    @Test
    fun `без подключённого доступа — CLIENT_NOT_FOUND`() =
        runTest {
            fixture.setUp()
            val ivan = fixture.client(status = ClientAppAccessStatus.INVITED)

            val result = fixture.asMom(ivan) { view.visits(ClientAppVisitsQuery(null, fixture.at(today, LocalTime(9, 0)))) }

            assertEquals("CLIENT_NOT_FOUND", attendance.errorCode(result))
        }
}
