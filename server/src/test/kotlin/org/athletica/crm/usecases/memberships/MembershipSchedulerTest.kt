package org.athletica.crm.usecases.memberships

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.subscription.ActivationReason
import org.athletica.crm.core.subscription.CloseMode
import org.athletica.crm.core.subscription.CloseReason
import org.athletica.crm.domain.audit.PostgresAuditLog
import org.athletica.crm.domain.auth.DbUsers
import org.athletica.crm.domain.employees.DbEmployees
import org.athletica.crm.domain.employees.DbRoles
import org.athletica.crm.domain.memberships.Activation
import org.athletica.crm.domain.memberships.ActiveMembership
import org.athletica.crm.domain.memberships.ClosedMembership
import org.athletica.crm.domain.memberships.InactiveMembership
import org.athletica.crm.domain.memberships.MembershipFixture
import org.athletica.crm.domain.notifications.DbNotifications
import org.athletica.crm.domain.org.DbOrgCalendar
import org.athletica.crm.domain.tasks.DbTasks
import org.athletica.crm.read.memberships.DbUnpaidSessionsView
import org.athletica.crm.security.PasswordHasher
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** DB-тесты фоновой обработки абонементов с подменённым временем. */
class MembershipSchedulerTest {
    private val fixture = MembershipFixture(timezone = "Asia/Yekaterinburg")
    private val scheduler =
        MembershipScheduler(
            db = TestPostgres.db,
            memberships = fixture.memberships,
            notifications = DbNotifications(),
            employees = DbEmployees(DbUsers(PasswordHasher()), DbRoles()),
            calendar = DbOrgCalendar(fixture.clock),
            unpaidSessions = DbUnpaidSessionsView(),
            tasks = DbTasks(),
            audit = PostgresAuditLog(),
            clock = fixture.clock,
        )

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking { fixture.setUp() }
    }

    private suspend fun notificationsCount(): Long =
        TestPostgres.db
            .sql("SELECT COUNT(*) AS c FROM notification_recipients WHERE employee_id = :e")
            .bind("e", fixture.employeeId)
            .firstOrNull { it.get("c", java.lang.Long::class.java)!!.toLong() } ?: 0

    @Test
    fun `активация по наступлении даты — с этой датой и системным автором`() =
        runTest {
            val issued = fixture.issue(fixture.insertClient(), Activation.OnDate(LocalDate(2026, 10, 20)))

            fixture.at(LocalDate(2026, 10, 19))
            scheduler.processDue()
            assertIs<InactiveMembership>(fixture.reload(issued.id))

            fixture.at(LocalDate(2026, 10, 20))
            scheduler.processDue()
            assertEquals(LocalDate(2026, 10, 20), assertIs<ActiveMembership>(fixture.reload(issued.id)).startDate)
            val actor =
                TestPostgres.db
                    .sql("SELECT actor_id, payload->>'reason' AS reason FROM membership_events WHERE membership_id = :id AND kind = 'ACTIVATED'")
                    .bind("id", issued.id)
                    .firstOrNull { (it.get("actor_id", java.util.UUID::class.java)?.toString() ?: "system") to it.get("reason", String::class.java) }
            assertEquals("system" to ActivationReason.ON_DATE.name, actor)
        }

    @Test
    fun `активация по крайней дате`() =
        runTest {
            val issued = fixture.issue(fixture.insertClient(), Activation.OnFirstVisit(LocalDate(2026, 10, 25)))

            fixture.at(LocalDate(2026, 10, 25))
            scheduler.processDue()

            assertEquals(LocalDate(2026, 10, 25), assertIs<ActiveMembership>(fixture.reload(issued.id)).startDate)
        }

    @Test
    fun `без крайней даты фоновой активации нет`() =
        runTest {
            val issued = fixture.issue(fixture.insertClient(), Activation.OnFirstVisit(null))

            fixture.at(LocalDate(2027, 10, 25))
            scheduler.processDue()

            assertIs<InactiveMembership>(fixture.reload(issued.id))
        }

    @Test
    fun `закрытие по сроку на следующий день после окончания`() =
        runTest {
            val issued = fixture.issue(fixture.insertClient(), closeMode = CloseMode.ON_EXPIRY)

            fixture.at(LocalDate(2026, 11, 15))
            scheduler.processDue()
            assertIs<ActiveMembership>(fixture.reload(issued.id))

            fixture.at(LocalDate(2026, 11, 16))
            scheduler.processDue()
            val closed = assertIs<ClosedMembership>(fixture.reload(issued.id))
            assertEquals(CloseReason.EXPIRY, closed.closeReason)
            assertEquals(0, notificationsCount())
        }

    @Test
    fun `ручной режим не закрывает сам и уведомляет выдавшего`() =
        runTest {
            val issued = fixture.issue(fixture.insertClient())

            fixture.at(LocalDate(2026, 11, 16))
            scheduler.processDue()

            val active = assertIs<ActiveMembership>(fixture.reload(issued.id))
            assertNotNull(active.closeNotifiedAt)
            assertEquals(1, notificationsCount())
            assertEquals(listOf("ISSUED", "ACTIVATED", "CLOSE_NOTIFIED"), fixture.eventKinds(issued.id))
        }

    @Test
    fun `уведомление не повторяется в эпизоде`() =
        runTest {
            val issued = fixture.issue(fixture.insertClient())
            fixture.setRemaining(issued.id, 0)

            fixture.at(LocalDate(2026, 10, 20))
            scheduler.processDue()
            fixture.at(LocalDate(2026, 11, 20))
            scheduler.processDue()

            assertEquals(1, notificationsCount())
        }

    @Test
    fun `закрытие по занятиям при вышедшем сроке уведомляет`() =
        runTest {
            val issued = fixture.issue(fixture.insertClient(), closeMode = CloseMode.ON_LAST_SESSION)
            fixture.setRemaining(issued.id, 2)

            fixture.at(LocalDate(2026, 11, 16))
            scheduler.processDue()

            assertIs<ActiveMembership>(fixture.reload(issued.id))
            assertEquals(1, notificationsCount())
        }

    @Test
    fun `закрытие по сроку не уведомляет об исчерпании и не пишет историю`() =
        runTest {
            val issued = fixture.issue(fixture.insertClient(), closeMode = CloseMode.ON_EXPIRY)
            fixture.setRemaining(issued.id, 0)

            fixture.at(LocalDate(2026, 10, 20))
            scheduler.processDue()

            assertNull(assertIs<ActiveMembership>(fixture.reload(issued.id)).closeNotifiedAt)
            assertEquals(0, notificationsCount())
            assertEquals(listOf("ISSUED", "ACTIVATED"), fixture.eventKinds(issued.id))
        }

    @Test
    fun `удалённому выдавшему уведомление не отправляется`() =
        runTest {
            val issued = fixture.issue(fixture.insertClient())
            TestPostgres.db.sql("UPDATE employees SET is_active = false WHERE id = :id").bind("id", fixture.employeeId).execute()

            fixture.at(LocalDate(2026, 11, 16))
            scheduler.processDue()

            assertNull(assertIs<ActiveMembership>(fixture.reload(issued.id)).closeNotifiedAt)
            assertEquals(0, notificationsCount())
        }

    @Test
    fun `повторный проход ничего не меняет`() =
        runTest {
            val client = fixture.insertClient()
            val onDate = fixture.issue(client, Activation.OnDate(LocalDate(2026, 10, 20)))
            val expiring = fixture.issue(client, closeMode = CloseMode.ON_EXPIRY)
            val manual = fixture.issue(client)

            fixture.at(LocalDate(2026, 11, 16))
            scheduler.processDue()
            val events = listOf(onDate, expiring, manual).map { fixture.eventKinds(it.id) }
            scheduler.processDue()

            assertEquals(events, listOf(onDate, expiring, manual).map { fixture.eventKinds(it.id) })
            assertEquals(1, notificationsCount())
        }
}
