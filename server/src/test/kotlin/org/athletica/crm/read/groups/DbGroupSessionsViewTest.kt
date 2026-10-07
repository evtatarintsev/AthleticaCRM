package org.athletica.crm.read.groups

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.groups.GroupSessionAttendanceSchema
import org.athletica.crm.api.schemas.groups.GroupSessionsResponse
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.sessions.SessionStatus
import org.athletica.crm.schedule.ScheduleFixture
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** DB-тесты проекции занятий группы: период, данные строки, последнее и ближайшее, скоуп. */
class DbGroupSessionsViewTest {
    private val fixture = ScheduleFixture()
    private val view = DbGroupSessionsView()
    private val from = LocalDate(2026, 3, 1)
    private val to = LocalDate(2026, 3, 31)

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking { fixture.setUp() }
    }

    private suspend fun list(
        groupId: GroupId,
        from: LocalDate = this.from,
        to: LocalDate = this.to,
    ): GroupSessionsResponse = fixture.inContext { view.list(GroupSessionsQuery(groupId, from, to)) }

    /** Занятие с 10:00 до 11:00 в [date]. */
    private suspend fun session(
        groupId: GroupId,
        hallId: HallId,
        date: LocalDate,
        status: String = "scheduled",
    ): SessionId = fixture.insertSession(groupId, hallId, date, LocalTime(10, 0), LocalTime(11, 0), status = status)

    private suspend fun insertClient(name: String): ClientId {
        val id = ClientId.new()
        TestPostgres.db
            .sql("INSERT INTO clients (id, org_id, name, gender) VALUES (:id, :orgId, :name, 'MALE'::gender)")
            .bind("id", id)
            .bind("orgId", fixture.orgId)
            .bind("name", name)
            .execute()
        return id
    }

    /** Строка журнала занятия [sessionId] для клиента [clientId] с отметкой [presence]. */
    private suspend fun attendance(
        sessionId: SessionId,
        clientId: ClientId,
        presence: String?,
    ) {
        TestPostgres.db
            .sql(
                """
                INSERT INTO session_attendance (session_id, client_id, kind, presence)
                VALUES (:s, :c, 'regular', :p::attendance_presence)
                """.trimIndent(),
            )
            .bind("s", sessionId)
            .bind("c", clientId)
            .bind("p", presence)
            .execute()
    }

    @Test
    fun `возвращает занятия группы и периода во всех статусах по дате и времени`() =
        runTest {
            val group = fixture.insertGroup()
            val other = fixture.insertGroup()
            val hall = fixture.insertHall()
            val evening = fixture.insertSession(group, hall, LocalDate(2026, 3, 2), LocalTime(18, 0), LocalTime(19, 0))
            val morning = session(group, hall, LocalDate(2026, 3, 2), status = "completed")
            val cancelled = session(group, hall, LocalDate(2026, 3, 10), status = "cancelled")
            session(group, hall, LocalDate(2026, 2, 28))
            session(group, hall, LocalDate(2026, 4, 1))
            session(other, hall, LocalDate(2026, 3, 5))

            val result = list(group).sessions

            assertEquals(listOf(morning, evening, cancelled), result.map { it.id })
            assertEquals(
                listOf(SessionStatus.COMPLETED, SessionStatus.SCHEDULED, SessionStatus.CANCELLED),
                result.map { it.status },
            )
        }

    @Test
    fun `строка содержит зал, тренеров занятия и признаки замены, переноса и ручного занятия`() =
        runTest {
            val group = fixture.insertGroup()
            val hall = fixture.insertHall("Большой зал")
            val slot =
                fixture.insertSlot(
                    group,
                    hall,
                    org.athletica.crm.core.DayOfWeek.THURSDAY,
                    LocalTime(10, 0),
                    LocalTime(11, 0),
                    from = from,
                )
            val moved =
                fixture.insertSession(
                    group,
                    hall,
                    LocalDate(2026, 3, 6),
                    LocalTime(10, 0),
                    LocalTime(11, 0),
                    originSlotId = slot,
                    originDate = LocalDate(2026, 3, 5),
                    isRescheduled = true,
                    isEmployeeAssignmentOverridden = true,
                )
            val replacement = fixture.insertEmployee("Замена")
            fixture.linkSessionEmployee(moved, replacement)
            val timeOnly =
                fixture.insertSession(
                    group,
                    hall,
                    LocalDate(2026, 3, 12),
                    LocalTime(12, 0),
                    LocalTime(13, 0),
                    originSlotId = slot,
                    originDate = LocalDate(2026, 3, 12),
                    isRescheduled = true,
                )
            val manual = session(group, hall, LocalDate(2026, 3, 20))

            val rows = list(group).sessions.associateBy { it.id }

            val movedRow = rows.getValue(moved)
            assertEquals("Большой зал", movedRow.hall.name)
            assertEquals(listOf("Замена"), movedRow.coaches.map { it.name })
            assertTrue(movedRow.coachesOverridden)
            assertEquals(LocalDate(2026, 3, 5), movedRow.rescheduledFrom)
            assertFalse(movedRow.isManual)
            assertEquals(LocalTime(10, 0), movedRow.startTime)
            assertEquals(LocalTime(11, 0), movedRow.endTime)

            assertNull(rows.getValue(timeOnly).rescheduledFrom)
            assertTrue(rows.getValue(manual).isManual)
            assertFalse(rows.getValue(manual).coachesOverridden)
            assertTrue(rows.getValue(manual).coaches.isEmpty())
        }

    @Test
    fun `посещаемость есть только у проведённого занятия`() =
        runTest {
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()
            val completed = session(group, hall, LocalDate(2026, 3, 2), status = "completed")
            val scheduled = session(group, hall, LocalDate(2026, 3, 4))
            val cancelled = session(group, hall, LocalDate(2026, 3, 6), status = "cancelled")
            val anna = insertClient("Аня")
            val boris = insertClient("Борис")
            val vera = insertClient("Вера")
            attendance(completed, anna, "present")
            attendance(completed, boris, "present")
            attendance(completed, vera, "absent")
            attendance(scheduled, anna, "present")
            attendance(cancelled, anna, "present")

            val rows = list(group).sessions.associateBy { it.id }

            assertEquals(GroupSessionAttendanceSchema(present = 2, total = 3), rows.getValue(completed).attendance)
            assertNull(rows.getValue(scheduled).attendance)
            assertNull(rows.getValue(cancelled).attendance)
        }

    @Test
    fun `последнее и ближайшее берутся вне периода и пропускают отменённые`() =
        runTest {
            val group = fixture.insertGroup()
            val hall = fixture.insertHall()
            session(group, hall, LocalDate(2020, 1, 10), status = "completed")
            val last = session(group, hall, LocalDate(2020, 1, 15), status = "scheduled")
            session(group, hall, LocalDate(2020, 1, 20), status = "cancelled")
            session(group, hall, LocalDate(2099, 1, 5), status = "cancelled")
            val next = session(group, hall, LocalDate(2099, 1, 10))
            session(group, hall, LocalDate(2099, 1, 15))

            val result = list(group)

            assertTrue(result.sessions.isEmpty())
            assertEquals(last, result.last?.id)
            assertEquals(next, result.next?.id)
        }

    @Test
    fun `у группы без занятий нет ни последнего, ни ближайшего`() =
        runTest {
            val group = fixture.insertGroup()

            val result = list(group)

            assertTrue(result.sessions.isEmpty())
            assertNull(result.last)
            assertNull(result.next)
        }

    @Test
    fun `группа другого филиала не найдена`() =
        runTest {
            val otherBranch = Uuid.generateV7()
            TestPostgres.db
                .sql("INSERT INTO branches (id, org_id, name) VALUES (:id, :orgId, :name)")
                .bind("id", otherBranch)
                .bind("orgId", fixture.orgId)
                .bind("name", "Другой")
                .execute()
            val otherGroup = GroupId.new()
            TestPostgres.db
                .sql("INSERT INTO groups (id, org_id, branch_id, name) VALUES (:id, :orgId, :branchId, :name)")
                .bind("id", otherGroup)
                .bind("orgId", fixture.orgId)
                .bind("branchId", otherBranch)
                .bind("name", "Чужая")
                .execute()

            val result = fixture.runInContext { view.list(GroupSessionsQuery(otherGroup, from, to)) }

            val error = assertIs<CommonDomainError>(result.leftOrNull())
            assertEquals("GROUP_NOT_FOUND", error.code)
        }

    @Test
    fun `группа другой организации не найдена`() =
        runTest {
            val foreign = ScheduleFixture()
            foreign.setUp("Чужая организация")
            val foreignGroup = foreign.insertGroup()

            val result = fixture.runInContext { view.list(GroupSessionsQuery(foreignGroup, from, to)) }

            assertIs<CommonDomainError>(result.leftOrNull())
        }
}
