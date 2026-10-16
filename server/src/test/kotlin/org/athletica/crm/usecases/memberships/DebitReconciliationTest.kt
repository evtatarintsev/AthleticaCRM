package org.athletica.crm.usecases.memberships

import arrow.core.Either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.subscription.CloseMode
import org.athletica.crm.domain.memberships.Activation
import org.athletica.crm.domain.memberships.ActiveMembership
import org.athletica.crm.domain.memberships.ClosedMembership
import org.athletica.crm.domain.memberships.DebitFixture
import org.athletica.crm.domain.memberships.InactiveMembership
import org.athletica.crm.domain.memberships.MembershipDebitConflict
import org.athletica.crm.domain.memberships.MembershipFixture
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** DB-тесты сверки списаний с журналом посещаемости. */
class DebitReconciliationTest {
    private val fixture = MembershipFixture()
    private val debits = DebitFixture(fixture)

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            fixture.setUp()
            debits.setUp()
        }
    }

    private fun date(day: Int, month: Int = 10) = LocalDate(2026, month, day)

    @Test
    fun `списание за присутствие уменьшает остаток и пишет историю`() =
        runTest {
            val client = fixture.insertClient()
            val issued = fixture.issue(client)
            val session = debits.session(date(16))
            debits.mark(session, client, "present")

            debits.reconcile(session, client)

            assertEquals(issued.id, debits.debitedFrom(session, client))
            assertEquals(7, fixture.reload(issued.id).sessionsRemaining)
            assertEquals(listOf("ISSUED", "ACTIVATED", "DEBITED"), fixture.eventKinds(issued.id))
        }

    @Test
    fun `отсутствие и открытый журнал не списываются`() =
        runTest {
            val client = fixture.insertClient()
            fixture.issue(client)
            val absent = debits.session(date(16))
            debits.mark(absent, client, "absent")
            val open = debits.session(date(16), status = "scheduled")
            debits.mark(open, client, "present")

            debits.reconcile(absent, client)
            debits.reconcile(open, client)

            assertNull(debits.debitedFrom(absent, client))
            assertNull(debits.debitedFrom(open, client))
        }

    @Test
    fun `повторная доставка не списывает второй раз`() =
        runTest {
            val client = fixture.insertClient()
            val issued = fixture.issue(client)
            val session = debits.session(date(16))
            debits.mark(session, client, "present")

            debits.reconcile(session, client)
            debits.reconcile(session, client)

            assertEquals(1, debits.debitRows(session, client))
            assertEquals(7, fixture.reload(issued.id).sessionsRemaining)
        }

    @Test
    fun `правка отметки возвращает и снова списывает`() =
        runTest {
            val client = fixture.insertClient()
            val issued = fixture.issue(client)
            val session = debits.session(date(16))
            debits.mark(session, client, "present")
            debits.reconcile(session, client)

            debits.mark(session, client, "absent")
            debits.reconcile(session, client)
            assertNull(debits.debitedFrom(session, client))
            assertEquals(8, fixture.reload(issued.id).sessionsRemaining)

            debits.mark(session, client, "present")
            debits.reconcile(session, client)
            assertEquals(issued.id, debits.debitedFrom(session, client))
            assertEquals(listOf("ISSUED", "ACTIVATED", "DEBITED", "DEBIT_REVERSED", "DEBITED"), fixture.eventKinds(issued.id))
        }

    @Test
    fun `обратный порядок событий — итог по текущему состоянию`() =
        runTest {
            val client = fixture.insertClient()
            fixture.issue(client)
            val session = debits.session(date(16))
            debits.mark(session, client, "absent")

            debits.reconcile(session, client)
            debits.reconcile(session, client)

            assertEquals(0, debits.debitRows(session, client))
        }

    @Test
    fun `закрытие последним списанием, возврат на закрытый и повторное пришёл`() =
        runTest {
            val client = fixture.insertClient()
            val issued = fixture.issue(client, closeMode = CloseMode.ON_LAST_SESSION, sessions = 1)
            val session = debits.session(date(16))
            debits.mark(session, client, "present")

            debits.reconcile(session, client)
            val closed = assertIs<ClosedMembership>(fixture.reload(issued.id))
            assertEquals(0, closed.sessionsRemaining)

            debits.mark(session, client, "absent")
            debits.reconcile(session, client)
            val returned = assertIs<ClosedMembership>(fixture.reload(issued.id))
            assertEquals(1, returned.sessionsRemaining)

            debits.mark(session, client, "present")
            debits.reconcile(session, client)
            assertNull(debits.debitedFrom(session, client))
            assertIs<ClosedMembership>(fixture.reload(issued.id))
        }

    @Test
    fun `активация первым посещением задним числом и не отменяется возвратом`() =
        runTest {
            val client = fixture.insertClient()
            val issued = fixture.issue(client, Activation.OnFirstVisit(null), issuedOn = date(8))
            fixture.at(date(12))
            val session = debits.session(date(10))
            debits.mark(session, client, "present")

            debits.reconcile(session, client)
            val active = assertIs<ActiveMembership>(fixture.reload(issued.id))
            assertEquals(date(10), active.startDate)

            debits.mark(session, client, "absent")
            debits.reconcile(session, client)
            assertEquals(date(10), assertIs<ActiveMembership>(fixture.reload(issued.id)).startDate)
        }

    @Test
    fun `поздно проведённое первое посещение — срок от даты занятия`() =
        runTest {
            val client = fixture.insertClient()
            val issued = fixture.issue(client, Activation.OnFirstVisit(null), issuedOn = date(8))
            fixture.at(date(20, 11))
            val session = debits.session(date(10))
            debits.mark(session, client, "present")

            debits.reconcile(session, client)

            val active = assertIs<ActiveMembership>(fixture.reload(issued.id))
            assertEquals(date(10), active.startDate)
            assertEquals(date(9, 11), active.endDate)
            assertEquals(issued.id, debits.debitedFrom(session, client))
        }

    @Test
    fun `первое посещение задаётся первым проведённым занятием`() =
        runTest {
            val client = fixture.insertClient()
            val issued = fixture.issue(client, Activation.OnFirstVisit(null), issuedOn = date(1))
            val earlier = debits.session(date(10))
            val later = debits.session(date(12))
            debits.mark(earlier, client, "present")
            debits.mark(later, client, "present")

            debits.reconcile(later, client)
            debits.reconcile(earlier, client)

            assertEquals(date(12), assertIs<ActiveMembership>(fixture.reload(issued.id)).startDate)
            assertNull(debits.debitedFrom(earlier, client))
        }

    @Test
    fun `занятие обработано до фоновой активации с даты`() =
        runTest {
            val client = fixture.insertClient()
            val issued = fixture.issue(client, Activation.OnDate(date(20)))
            fixture.at(date(20))
            val session = debits.session(date(20))
            debits.mark(session, client, "present")

            debits.reconcile(session, client)

            assertEquals(date(20), assertIs<ActiveMembership>(fixture.reload(issued.id)).startDate)
            assertEquals(issued.id, debits.debitedFrom(session, client))
        }

    @Test
    fun `занятие чужой группы не списывается`() =
        runTest {
            val client = fixture.insertClient()
            fixture.issue(client)
            val otherGroup = GroupId.new()
            TestPostgres.db
                .sql("INSERT INTO groups (id, org_id, branch_id, name) VALUES (:id, :orgId, :branchId, 'Взрослые')")
                .bind("id", otherGroup)
                .bind("orgId", fixture.orgId)
                .bind("branchId", fixture.branchId)
                .execute()
            val session = debits.session(date(16), groupId = otherGroup)
            debits.mark(session, client, "present")

            debits.reconcile(session, client)

            assertNull(debits.debitedFrom(session, client))
        }

    @Test
    fun `конкурентные списания с последнего занятия`() =
        runTest {
            val client = fixture.insertClient()
            val issued = fixture.issue(client, sessions = 1)
            val first = debits.session(date(16))
            val second = debits.session(date(17))
            debits.mark(first, client, "present")
            debits.mark(second, client, "present")

            withContext(Dispatchers.IO) {
                listOf(first, second).map { async { runCatching { debits.reconcile(it, client) } } }.awaitAll()
            }

            assertEquals(0, fixture.reload(issued.id).sessionsRemaining)
            val debited = listOf(first, second).mapNotNull { debits.debitedFrom(it, client) }
            assertEquals(listOf(issued.id), debited)
        }

    @Test
    fun `перехваченный остаток — исключение для повтора, а не долг`() =
        runTest {
            val client = fixture.insertClient()
            val issued = fixture.issue(client, sessions = 1)
            val session = debits.session(date(16))
            val stale = assertIs<ActiveMembership>(fixture.reload(issued.id))
            fixture.setRemaining(issued.id, 0)

            assertFailsWith<MembershipDebitConflict> {
                fixture.tx(fixture.system) { stale.debit(session, date(16), date(16)) }
            }
            assertEquals(0, debits.debitRows(session, client))
        }

    @Test
    fun `исчерпание списанием уведомляет, возврат открывает новый эпизод`() =
        runTest {
            val client = fixture.insertClient()
            val issued = fixture.issue(client, sessions = 1)
            val session = debits.session(date(16))
            debits.mark(session, client, "present")

            debits.reconcile(session, client)
            assertEquals(1, debits.notifications())
            assertNotNull(assertIs<ActiveMembership>(fixture.reload(issued.id)).closeNotifiedAt)

            debits.mark(session, client, "absent")
            debits.reconcile(session, client)
            assertNull(assertIs<ActiveMembership>(fixture.reload(issued.id)).closeNotifiedAt)

            debits.mark(session, client, "present")
            debits.reconcile(session, client)
            assertEquals(2, debits.notifications())
        }

    @Test
    fun `отзыв абонемента со списаниями отклоняется`() =
        runTest {
            val client = fixture.insertClient()
            val issued = fixture.issue(client)
            val session = debits.session(date(16))
            debits.mark(session, client, "present")
            debits.reconcile(session, client)

            val result = fixture.attempt { revokeMembership(fixture.memberships, issued.id) }

            assertEquals("MEMBERSHIP_HAS_DEBITS", assertIs<Either.Left<DomainError>>(result).value.code)
            assertIs<ActiveMembership>(fixture.reload(issued.id))
        }

    @Test
    fun `неактивированный остаётся, если есть активный`() =
        runTest {
            val client = fixture.insertClient()
            val active = fixture.issue(client)
            val firstVisit = fixture.issue(client, Activation.OnFirstVisit(null))
            val session = debits.session(date(16))
            debits.mark(session, client, "present")

            debits.reconcile(session, client)

            assertEquals(active.id, debits.debitedFrom(session, client))
            assertIs<InactiveMembership>(fixture.reload(firstVisit.id))
        }
}
