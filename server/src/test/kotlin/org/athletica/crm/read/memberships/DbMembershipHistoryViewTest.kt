package org.athletica.crm.read.memberships

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.memberships.MembershipActivatedEventSchema
import org.athletica.crm.api.schemas.memberships.MembershipCloseNotifiedEventSchema
import org.athletica.crm.api.schemas.memberships.MembershipIssuedEventSchema
import org.athletica.crm.core.subscription.ActivationMode
import org.athletica.crm.core.subscription.ActivationReason
import org.athletica.crm.core.subscription.CloseMode
import org.athletica.crm.domain.auth.DbUsers
import org.athletica.crm.domain.employees.DbEmployees
import org.athletica.crm.domain.employees.DbRoles
import org.athletica.crm.domain.memberships.MembershipFixture
import org.athletica.crm.domain.notifications.DbNotifications
import org.athletica.crm.domain.org.DbOrgCalendar
import org.athletica.crm.domain.tasks.DbTasks
import org.athletica.crm.read.memberships.DbUnpaidSessionsView
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.usecases.memberships.MembershipScheduler
import org.athletica.crm.usecases.memberships.closeMembership
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

/** DB-тесты проекции истории абонемента. */
class DbMembershipHistoryViewTest {
    private val fixture = MembershipFixture()
    private val view = DbMembershipHistoryView()
    private val scheduler =
        MembershipScheduler(
            db = TestPostgres.db,
            memberships = fixture.memberships,
            notifications = DbNotifications(),
            employees = DbEmployees(DbUsers(PasswordHasher()), DbRoles()),
            calendar = DbOrgCalendar(fixture.clock),
            unpaidSessions = DbUnpaidSessionsView(),
            tasks = DbTasks(),
            clock = fixture.clock,
        )

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking { fixture.setUp() }
    }

    private suspend fun history(id: org.athletica.crm.core.entityids.MembershipId) = fixture.tx { context(fixture.ctx) { view.byMembership(id) } }.entries

    @Test
    fun `выдача сразу даёт две записи от выдавшего, новые сверху`() =
        runTest {
            val issued = fixture.issue(fixture.insertClient())

            val entries = history(issued.id)

            assertEquals(2, entries.size)
            val activated = assertIs<MembershipActivatedEventSchema>(entries[0].event)
            assertEquals(ActivationReason.ON_ISSUE, activated.reason)
            assertEquals(LocalDate(2026, 10, 16), activated.startDate)
            val issuedEvent = assertIs<MembershipIssuedEventSchema>(entries[1].event)
            assertEquals(ActivationMode.IMMEDIATE, issuedEvent.activationMode)
            assertEquals(CloseMode.MANUAL, issuedEvent.closeMode)
            entries.forEach {
                assertEquals(fixture.employeeId, it.actorId)
                assertEquals("Админ", it.actorName)
            }
        }

    @Test
    fun `отправка уведомления к закрытию — системная запись`() =
        runTest {
            val issued = fixture.issue(fixture.insertClient())
            fixture.at(LocalDate(2026, 11, 16))
            scheduler.processDue()

            val latest = history(issued.id).first()

            assertIs<MembershipCloseNotifiedEventSchema>(latest.event)
            assertNull(latest.actorId)
            assertNull(latest.actorName)
        }

    @Test
    fun `исчерпание у режима по сроку записи не даёт`() =
        runTest {
            val issued = fixture.issue(fixture.insertClient(), closeMode = CloseMode.ON_EXPIRY)
            fixture.setRemaining(issued.id, 0)
            fixture.at(LocalDate(2026, 10, 20))
            scheduler.processDue()

            assertEquals(2, history(issued.id).size)
        }

    @Test
    fun `откат транзакции не оставляет записи`() =
        runTest {
            val issued = fixture.issue(fixture.insertClient())

            assertFailsWith<IllegalStateException> {
                fixture.tx {
                    closeMembership(fixture.memberships, DbOrgCalendar(fixture.clock), issued.id)
                    error("сбой после закрытия")
                }
            }

            assertEquals(2, history(issued.id).size)
        }
}
