package org.athletica.crm.usecases.sessions

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.domain.auth.DbUsers
import org.athletica.crm.domain.employees.DbEmployees
import org.athletica.crm.domain.employees.DbRoles
import org.athletica.crm.domain.events.DomainEventBus
import org.athletica.crm.domain.groups.DbGroups
import org.athletica.crm.domain.sessions.DbSessions
import org.athletica.crm.schedule.ScheduleFixture
import org.athletica.crm.security.PasswordHasher
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.uuid.Uuid

/**
 * Тесты создания занятия вне расписания и проверки зала при создании и переносе:
 * явный состав тренеров без признака замены и принадлежность зала филиалу группы.
 */
class CreateSessionTest {
    private val groups = DbGroups()
    private val sessions = DbSessions(DomainEventBus())
    private val employees = DbEmployees(DbUsers(PasswordHasher()), DbRoles())
    private val fixture = ScheduleFixture()
    private val date = LocalDate(2026, 10, 22)

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    /** Создаёт занятие группы [groupId] в зале [hallId] с тренерами [employeeIds]. */
    private suspend fun create(
        groupId: GroupId,
        hallId: HallId,
        employeeIds: List<EmployeeId>,
        id: SessionId = SessionId.new(),
    ) = fixture.runInContext {
        createSession(
            groups = groups,
            sessions = sessions,
            employees = employees,
            id = id,
            groupId = groupId,
            date = date,
            startTime = LocalTime(15, 0),
            endTime = LocalTime(16, 0),
            hallId = hallId,
            employeeIds = employeeIds,
            notes = null,
        )
    }

    /** Создаёт филиал организации теста и зал в нём. */
    private suspend fun hallInOtherBranch(): HallId {
        val branch = Uuid.generateV7()
        TestPostgres.db
            .sql("INSERT INTO branches (id, org_id, name) VALUES (:id, :orgId, 'Второй')")
            .bind("id", branch)
            .bind("orgId", fixture.orgId)
            .execute()
        return insertHall(fixture.orgId, branch)
    }

    /** Создаёт отдельную организацию с филиалом и зал в ней. */
    private suspend fun hallInOtherOrg(): HallId {
        val org = Uuid.generateV7()
        val branch = Uuid.generateV7()
        TestPostgres.db
            .sql("INSERT INTO organizations (id, name) VALUES (:id, 'Чужая')")
            .bind("id", org)
            .execute()
        TestPostgres.db
            .sql("INSERT INTO branches (id, org_id, name) VALUES (:id, :orgId, 'Чужой')")
            .bind("id", branch)
            .bind("orgId", org)
            .execute()
        return insertHall(org, branch)
    }

    /** Вставляет зал в филиал [branch] организации [org]. */
    private suspend fun insertHall(org: Uuid, branch: Uuid): HallId {
        val id = HallId.new()
        TestPostgres.db
            .sql("INSERT INTO halls (id, org_id, branch_id, name) VALUES (:id, :orgId, :branchId, 'Зал')")
            .bind("id", id)
            .bind("orgId", org)
            .bind("branchId", branch)
            .execute()
        return id
    }

    /** Ожидает ошибку домена с кодом [code]. */
    private fun assertError(code: String, result: Either<*, *>) {
        val error = assertIs<Either.Left<*>>(result).value
        assertEquals(code, assertIs<CommonDomainError>(error).code)
    }

    @Test
    fun `сохраняется переданный состав тренеров без признака замены`() =
        runTest {
            fixture.setUp()
            val hall = fixture.insertHall()
            val group = fixture.insertGroup()
            val groupTrainer = fixture.insertEmployee()
            val otherTrainer = fixture.insertEmployee()
            fixture.linkGroupEmployee(group, groupTrainer)
            val id = SessionId.new()

            assertIs<Either.Right<*>>(create(group, hall, listOf(otherTrainer), id))

            val session = fixture.inContext { sessions.byId(id) }
            assertEquals(listOf(otherTrainer), session.employeeIds)
            assertFalse(session.isEmployeeAssignmentOverridden)
            assertEquals(null, session.originSlotId)
        }

    @Test
    fun `пустой состав не подставляет тренеров группы`() =
        runTest {
            fixture.setUp()
            val hall = fixture.insertHall()
            val group = fixture.insertGroup()
            fixture.linkGroupEmployee(group, fixture.insertEmployee())
            val id = SessionId.new()

            assertIs<Either.Right<*>>(create(group, hall, emptyList(), id))

            assertEquals(emptyList(), fixture.inContext { sessions.byId(id) }.employeeIds)
        }

    @Test
    fun `тренер без доступа к филиалу группы отклоняется`() =
        runTest {
            fixture.setUp()
            val hall = fixture.insertHall()
            val group = fixture.insertGroup()
            val stranger = EmployeeId.new()
            TestPostgres.db
                .sql(
                    """
                    INSERT INTO employees (id, org_id, name, is_active, all_branches_access, joined_at)
                    VALUES (:id, :orgId, 'Чужой', true, false, NOW())
                    """.trimIndent(),
                )
                .bind("id", stranger)
                .bind("orgId", fixture.orgId)
                .execute()

            assertError("EMPLOYEE_NOT_FOUND", create(group, hall, listOf(stranger)))
            assertEquals(0L, fixture.sessionCount())
        }

    @Test
    fun `зал другого филиала при создании отклоняется`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()

            assertError("HALL_NOT_FOUND", create(group, hallInOtherBranch(), emptyList()))
            assertEquals(0L, fixture.sessionCount())
        }

    @Test
    fun `зал другой организации при создании отклоняется`() =
        runTest {
            fixture.setUp()
            val group = fixture.insertGroup()

            assertError("HALL_NOT_FOUND", create(group, hallInOtherOrg(), emptyList()))
            assertEquals(0L, fixture.sessionCount())
        }

    @Test
    fun `перенос в зал другого филиала отклоняется и не меняет занятие`() =
        runTest {
            fixture.setUp()
            val hall = fixture.insertHall()
            val group = fixture.insertGroup()
            val id = fixture.insertSession(group, hall, date, LocalTime(15, 0), LocalTime(16, 0))
            val otherHall = hallInOtherBranch()

            val result =
                fixture.runInContext {
                    sessions.byId(id).reschedule(LocalDate(2026, 10, 23), LocalTime(17, 0), LocalTime(18, 0), otherHall)
                }

            assertError("HALL_NOT_FOUND", result)
            val session = fixture.inContext { sessions.byId(id) }
            assertEquals(date, session.date)
            assertEquals(LocalTime(15, 0), session.startTime)
            assertEquals(hall, session.hallId)
        }

    @Test
    fun `создание и перенос в зал филиала группы проходят`() =
        runTest {
            fixture.setUp()
            val hall = fixture.insertHall()
            val otherHall = fixture.insertHall()
            val group = fixture.insertGroup()
            val id = SessionId.new()

            assertIs<Either.Right<*>>(create(group, hall, emptyList(), id))
            fixture.inContext {
                sessions.byId(id).reschedule(LocalDate(2026, 10, 23), LocalTime(17, 0), LocalTime(18, 0), otherHall)
            }

            assertEquals(otherHall, fixture.inContext { sessions.byId(id) }.hallId)
        }
}
