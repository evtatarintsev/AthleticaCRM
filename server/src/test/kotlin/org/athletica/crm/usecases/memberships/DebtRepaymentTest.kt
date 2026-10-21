package org.athletica.crm.usecases.memberships

import arrow.core.Either
import arrow.core.raise.context.Raise
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.domain.attendance.DbParticipations
import org.athletica.crm.domain.audit.PostgresAuditLog
import org.athletica.crm.domain.auth.DbUsers
import org.athletica.crm.domain.employees.DbEmployees
import org.athletica.crm.domain.employees.DbRoles
import org.athletica.crm.domain.memberships.Activation
import org.athletica.crm.domain.memberships.ActiveMembership
import org.athletica.crm.domain.memberships.DebitFixture
import org.athletica.crm.domain.memberships.InactiveMembership
import org.athletica.crm.domain.memberships.MembershipFixture
import org.athletica.crm.domain.notifications.DbNotifications
import org.athletica.crm.domain.org.DbOrgCalendar
import org.athletica.crm.domain.tasks.DbTasks
import org.athletica.crm.domain.tasks.Task
import org.athletica.crm.domain.tasks.Tasks
import org.athletica.crm.read.memberships.DbUnpaidSessionsView
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.storage.Transaction
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** DB-тесты неоплаченных занятий, погашения долга и задачи «Проверить клиента». */
class DebtRepaymentTest {
    private val fixture = MembershipFixture()
    private val debits = DebitFixture(fixture)
    private val unpaid = DbUnpaidSessionsView()
    private val calendar = DbOrgCalendar(fixture.clock)

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            fixture.setUp()
            debits.setUp()
        }
    }

    private fun date(day: Int) = LocalDate(2026, 10, day)

    /** Проведённое занятие от [day], клиент [client] отмечен «пришёл», сверка выполнена. */
    private suspend fun attended(client: ClientId, day: Int, presence: String = "present"): SessionId {
        val session = debits.session(date(day))
        debits.mark(session, client, presence)
        debits.reconcile(session, client)
        return session
    }

    private suspend fun unpaidOf(id: MembershipId) = fixture.tx { unpaid.byMembership(id) }.sessions.map { it.sessionId }

    private suspend fun repay(id: MembershipId, sessions: List<SessionId>) = fixture.attempt { repayDebt(fixture.memberships, DbParticipations(), calendar, id, sessions) }

    private suspend fun tasksForClient(client: ClientId): List<String> =
        TestPostgres.db
            .sql("SELECT description FROM tasks WHERE client_id = :c AND created_by IS NULL AND assignee_id IS NULL")
            .bind("c", client)
            .list { it.get("description", String::class.java)!! }

    @Test
    fun `посещение без абонемента — неоплаченное, отменённое и отсутствие — нет`() =
        runTest {
            val client = fixture.insertClient()
            val unpaidSession = attended(client, 10)
            attended(client, 11, presence = "absent")
            val cancelled = debits.session(date(12), status = "cancelled")
            debits.mark(cancelled, client, "present")
            val issued = fixture.issue(client)

            assertEquals(listOf(unpaidSession), unpaidOf(issued.id))
        }

    @Test
    fun `погашение занятия до начала срока — дата начала не меняется`() =
        runTest {
            val client = fixture.insertClient()
            val session = attended(client, 10)
            val issued = fixture.issue(client, Activation.OnDate(date(11)), issuedOn = date(16))

            assertIs<Either.Right<*>>(repay(issued.id, listOf(session)))

            val active = assertIs<ActiveMembership>(fixture.reload(issued.id))
            assertEquals(date(11), active.startDate)
            assertEquals(7, active.sessionsRemaining)
            assertEquals(emptyList(), unpaidOf(issued.id))
            assertEquals("DEBITED", fixture.eventKinds(issued.id).last())
        }

    @Test
    fun `частичное погашение`() =
        runTest {
            val client = fixture.insertClient()
            val sessions = listOf(attended(client, 10), attended(client, 11), attended(client, 12))
            val issued = fixture.issue(client)

            repay(issued.id, sessions.take(2))

            assertEquals(listOf(sessions[2]), unpaidOf(issued.id))
            assertEquals(6, fixture.reload(issued.id).sessionsRemaining)
        }

    @Test
    fun `больше остатка отклоняется, ничего не списано`() =
        runTest {
            val client = fixture.insertClient()
            val sessions = listOf(attended(client, 10), attended(client, 11))
            val issued = fixture.issue(client, sessions = 1)

            val result = repay(issued.id, sessions)

            assertEquals("DEBT_EXCEEDS_REMAINING", assertIs<Either.Left<DomainError>>(result).value.code)
            assertEquals(2, unpaidOf(issued.id).size)
            assertEquals(1, fixture.reload(issued.id).sessionsRemaining)
        }

    @Test
    fun `погашение с неактивного отклоняется`() =
        runTest {
            val client = fixture.insertClient()
            val session = attended(client, 10)
            val issued = fixture.issue(client)
            fixture.tx { closeMembership(fixture.memberships, calendar, issued.id) }

            val result = repay(issued.id, listOf(session))

            assertEquals("MEMBERSHIP_NOT_ACTIVE", assertIs<Either.Left<DomainError>>(result).value.code)
        }

    @Test
    fun `занятие уже списано параллельно`() =
        runTest {
            val client = fixture.insertClient()
            val session = attended(client, 10)
            val first = fixture.issue(client, name = "Первый")
            val second = fixture.issue(client, name = "Второй")
            repay(first.id, listOf(session))

            val result = repay(second.id, listOf(session))

            assertEquals("ALREADY_DEBITED", assertIs<Either.Left<DomainError>>(result).value.code)
            assertEquals(8, fixture.reload(second.id).sessionsRemaining)
        }

    @Test
    fun `задача после фоновой активации при долгах`() =
        runTest {
            val client = fixture.insertClient()
            attended(client, 10)
            attended(client, 11)
            fixture.issue(client, Activation.OnDate(date(20)))
            fixture.at(date(20))

            schedulerFor().processDue()

            val tasks = tasksForClient(client)
            assertEquals(1, tasks.size)
            assertTrue(tasks.single().contains("2"))
        }

    @Test
    fun `без долгов задачи нет`() =
        runTest {
            val client = fixture.insertClient()
            fixture.issue(client, Activation.OnDate(date(20)))
            fixture.at(date(20))

            schedulerFor().processDue()

            assertEquals(emptyList(), tasksForClient(client))
        }

    @Test
    fun `активация первым посещением при долгах — активировавшее занятие не в счёте`() =
        runTest {
            val client = fixture.insertClient()
            attended(client, 1)
            attended(client, 3)
            val issued = fixture.issue(client, Activation.OnFirstVisit(null), issuedOn = date(5))
            fixture.at(date(10))

            val activating = attended(client, 10)

            assertEquals(issued.id, debits.debitedFrom(activating, client))
            val tasks = tasksForClient(client)
            assertEquals(1, tasks.size)
            assertTrue(tasks.single().contains(": 2."), tasks.single())
        }

    @Test
    fun `первое посещение без других долгов — задачи нет`() =
        runTest {
            val client = fixture.insertClient()
            fixture.issue(client, Activation.OnFirstVisit(null), issuedOn = date(5))
            fixture.at(date(10))

            attended(client, 10)

            assertEquals(emptyList(), tasksForClient(client))
        }

    @Test
    fun `ошибка создания задачи откатывает активацию и списание`() =
        runTest {
            val client = fixture.insertClient()
            attended(client, 1)
            val issued = fixture.issue(client, Activation.OnFirstVisit(null), issuedOn = date(5))
            fixture.at(date(10))
            val failingTasks =
                object : Tasks by DbTasks() {
                    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
                    override suspend fun new(
                        id: TaskId,
                        title: String,
                        description: String,
                        clientId: ClientId?,
                        dueDate: Instant?,
                        dueDateEnd: Instant?,
                    ): Task = error("сбой создания задачи")
                }
            val reconciliation =
                DebitReconciliation(
                    TestPostgres.db,
                    fixture.memberships,
                    DbParticipations(),
                    DbNotifications(),
                    DbEmployees(DbUsers(PasswordHasher()), DbRoles()),
                    calendar,
                    unpaid,
                    failingTasks,
                    PostgresAuditLog(),
                )
            val session = debits.session(date(10))
            debits.mark(session, client, "present")

            runCatching { reconciliation.reconcile(fixture.orgId, session, client) }

            assertNull(debits.debitedFrom(session, client))
            assertIs<InactiveMembership>(fixture.reload(issued.id))
        }

    private fun schedulerFor() =
        MembershipScheduler(
            db = TestPostgres.db,
            memberships = fixture.memberships,
            notifications = DbNotifications(),
            employees = DbEmployees(DbUsers(PasswordHasher()), DbRoles()),
            calendar = calendar,
            unpaidSessions = unpaid,
            tasks = DbTasks(),
            audit = PostgresAuditLog(),
            clock = fixture.clock,
        )
}
