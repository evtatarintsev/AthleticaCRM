package org.athletica.crm.usecases.memberships

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.memberships.MembershipFrozenEventSchema
import org.athletica.crm.api.schemas.memberships.MembershipUnfrozenEventSchema
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.subscription.CloseMode
import org.athletica.crm.core.subscription.FreezeStatus
import org.athletica.crm.core.subscription.UnfreezeReason
import org.athletica.crm.domain.auth.DbUsers
import org.athletica.crm.domain.employees.DbEmployees
import org.athletica.crm.domain.employees.DbRoles
import org.athletica.crm.domain.memberships.Activation
import org.athletica.crm.domain.memberships.ActiveMembership
import org.athletica.crm.domain.memberships.DebitFixture
import org.athletica.crm.domain.memberships.MembershipFixture
import org.athletica.crm.domain.memberships.MembershipFreeze
import org.athletica.crm.domain.notifications.DbNotifications
import org.athletica.crm.domain.org.DbOrgCalendar
import org.athletica.crm.domain.tasks.DbTasks
import org.athletica.crm.read.memberships.DbMembershipHistoryView
import org.athletica.crm.read.memberships.DbUnpaidSessionsView
import org.athletica.crm.security.PasswordHasher
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DB-тесты заморозки абонемента: оформление, снятие, посещение, планировщик и аудит. */
class MembershipFreezeTest {
    private val fixture = MembershipFixture()
    private val debits = DebitFixture(fixture)
    private val calendar = DbOrgCalendar(fixture.clock)
    private val scheduler =
        MembershipScheduler(
            db = TestPostgres.db,
            memberships = fixture.memberships,
            notifications = DbNotifications(),
            employees = DbEmployees(DbUsers(PasswordHasher()), DbRoles()),
            calendar = calendar,
            unpaidSessions = DbUnpaidSessionsView(),
            tasks = DbTasks(),
            clock = fixture.clock,
        )

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            fixture.setUp()
            debits.setUp()
        }
    }

    private fun date(day: Int, month: Int = 11) = LocalDate(2026, month, day)

    /** Активный абонемент на ноябрь (01.11–30.11) с режимом закрытия [closeMode]; сегодня 01.11. */
    private suspend fun november(client: ClientId, closeMode: CloseMode = CloseMode.MANUAL, name: String = "Ноябрь"): MembershipId {
        fixture.at(date(1))
        return fixture.issue(client, closeMode = closeMode, issuedOn = date(1), name = name).id
    }

    private suspend fun freeze(
        id: MembershipId,
        start: LocalDate,
        end: LocalDate,
        reason: String = "отпуск",
    ) = fixture.attempt { freezeMembership(fixture.memberships, calendar, id, start, end, reason) }

    private suspend fun unfreeze(id: MembershipId) = fixture.tx { unfreezeMembership(fixture.memberships, calendar, id) }

    private suspend fun active(id: MembershipId) = assertIs<ActiveMembership>(fixture.reload(id))

    private suspend fun errorCode(
        id: MembershipId,
        start: LocalDate,
        end: LocalDate,
        reason: String = "отпуск",
    ) = assertIs<CommonDomainError>(freeze(id, start, end, reason).leftOrNull()).code

    /** Записи аудита абонемента [id]: имя автора и данные, от старых к новым. */
    private suspend fun auditRows(id: MembershipId): List<Pair<String, String>> =
        TestPostgres.db
            .sql("SELECT username, data::text AS data FROM audit_logs WHERE entity_id = :id ORDER BY created_at, id")
            .bind("id", id)
            .list { it.get("username", String::class.java)!! to it.get("data", String::class.java)!! }

    @Test
    fun `правила заморозки — состояние и неиспользованные дни`() {
        val freeze = MembershipFreeze(date(1), date(21), "отпуск", null)
        assertEquals(21, freeze.days)
        assertEquals(FreezeStatus.SCHEDULED, freeze.statusAt(date(31, 10)))
        assertEquals(FreezeStatus.ACTIVE, freeze.statusAt(date(21)))
        assertNull(freeze.statusAt(date(22)))
        assertEquals(7, freeze.unusedDaysFrom(date(15)))
        assertEquals(21, freeze.unusedDaysFrom(date(1)))
        assertEquals(21, freeze.unusedDaysFrom(date(20, 10)))
        assertEquals(0, freeze.unusedDaysFrom(date(22)))
    }

    @Test
    fun `заморозка сдвигает срок сразу и пишется в историю и аудит`() =
        runTest {
            val id = november(fixture.insertClient())

            freeze(id, date(1), date(14))

            val frozen = active(id)
            assertEquals(date(14, 12), frozen.endDate)
            assertEquals(MembershipFreeze(date(1), date(14), "отпуск", fixture.employeeId), frozen.freeze)
            assertEquals("FROZEN", fixture.eventKinds(id).last())
            val (author, data) = auditRows(id).last()
            assertEquals(fixture.ctx.username, author)
            assertTrue(data.contains("отпуск"))
        }

    @Test
    fun `недопустимые заморозки отклоняются без изменений`() =
        runTest {
            val id = november(fixture.insertClient())
            fixture.at(date(5))

            assertEquals("FREEZE_PERIOD_INVALID", errorCode(id, date(10), date(9)))
            assertEquals("FREEZE_IN_PAST", errorCode(id, date(4), date(9)))
            assertEquals("FREEZE_AFTER_END", errorCode(id, date(1, 12), date(9, 12)))
            assertEquals("FREEZE_REASON_REQUIRED", errorCode(id, date(10), date(12), " "))
            freeze(id, date(10), date(12))
            assertEquals("MEMBERSHIP_ALREADY_FROZEN", errorCode(id, date(20), date(22)))

            val inactive = fixture.issue(fixture.insertClient(), Activation.OnFirstVisit(null), issuedOn = date(1)).id
            assertEquals("MEMBERSHIP_NOT_ACTIVE", errorCode(inactive, date(10), date(12)))
            assertEquals(date(3, 12), active(id).endDate)
            assertEquals(1, auditRows(id).count { it.second.contains("\"FROZEN\"") })
        }

    @Test
    fun `ручная разморозка возвращает неиспользованные дни`() =
        runTest {
            val id = november(fixture.insertClient())
            freeze(id, date(1), date(21))

            fixture.at(date(15))
            unfreeze(id)

            val unfrozen = active(id)
            assertNull(unfrozen.freeze)
            assertEquals(date(14, 12), unfrozen.endDate)
            assertEquals(listOf("FROZEN", "UNFROZEN"), fixture.eventKinds(id).takeLast(2))
            assertTrue(auditRows(id).last().second.contains(UnfreezeReason.MANUAL.name))
        }

    @Test
    fun `ручная разморозка в день начала не сдвигает срок`() =
        runTest {
            val id = november(fixture.insertClient())
            freeze(id, date(1), date(21))

            unfreeze(id)

            assertEquals(date(30), active(id).endDate)
        }

    @Test
    fun `история показывает заморозку и её снятие`() =
        runTest {
            val id = november(fixture.insertClient())
            freeze(id, date(10), date(20))
            unfreeze(id)

            val events = fixture.tx { context(fixture.ctx) { DbMembershipHistoryView().byMembership(id) } }.entries.map { it.event }

            assertEquals(MembershipUnfrozenEventSchema(UnfreezeReason.CANCELLED, date(10), date(20), date(30)), events[0])
            assertEquals(MembershipFrozenEventSchema(date(10), date(20), "отпуск", date(11, 12)), events[1])
        }

    @Test
    fun `отмена запланированной заморозки возвращает исходный срок`() =
        runTest {
            val id = november(fixture.insertClient())
            freeze(id, date(10), date(20))

            unfreeze(id)

            assertEquals(date(30), active(id).endDate)
            assertTrue(auditRows(id).last().second.contains(UnfreezeReason.CANCELLED.name))
        }

    @Test
    fun `разморозка без заморозки — ошибка`() =
        runTest {
            val id = november(fixture.insertClient())

            val result = fixture.attempt { unfreezeMembership(fixture.memberships, calendar, id) }

            assertEquals("MEMBERSHIP_NOT_FROZEN", assertIs<CommonDomainError>(result.leftOrNull()).code)
        }

    @Test
    fun `замороженный абонемент не закрывается по исходному сроку, планировщик снимает закончившуюся заморозку`() =
        runTest {
            val id = november(fixture.insertClient(), CloseMode.ON_EXPIRY)
            fixture.at(date(25))
            freeze(id, date(25), date(5, 12))

            fixture.at(date(1, 12))
            scheduler.processDue()
            assertEquals(FreezeStatus.ACTIVE, active(id).freeze?.statusAt(date(1, 12)))

            fixture.at(date(6, 12))
            scheduler.processDue()
            val after = active(id)
            assertNull(after.freeze)
            assertEquals(date(11, 12), after.endDate)
            val (author, data) = auditRows(id).last()
            assertEquals("Система", author)
            assertTrue(data.contains(UnfreezeReason.EXPIRED.name))
        }

    @Test
    fun `можно заморозить снова после снятия`() =
        runTest {
            val id = november(fixture.insertClient())
            freeze(id, date(1), date(3))
            fixture.at(date(10))

            freeze(id, date(10), date(12))

            assertEquals(MembershipFreeze(date(10), date(12), "отпуск", fixture.employeeId), active(id).freeze)
            assertEquals(date(6, 12), active(id).endDate)
            assertEquals(listOf("FROZEN", "UNFROZEN", "FROZEN"), fixture.eventKinds(id).takeLast(3))
        }

    @Test
    fun `посещение в период заморозки снимает её и списывается с абонемента`() =
        runTest {
            val client = fixture.insertClient()
            val id = november(client)
            freeze(id, date(1), date(21))
            fixture.at(date(15))
            val session = debits.session(date(15))
            debits.mark(session, client, "present")

            debits.reconcile(session, client)
            debits.reconcile(session, client)

            val after = active(id)
            assertNull(after.freeze)
            assertEquals(date(14, 12), after.endDate)
            assertEquals(id, debits.debitedFrom(session, client))
            assertEquals(7, after.sessionsRemaining)
            assertTrue(auditRows(id).last().second.contains(UnfreezeReason.VISIT.name))
        }

    @Test
    fun `посещение в день начала заморозки снимает её без сдвига`() =
        runTest {
            val client = fixture.insertClient()
            val id = november(client)
            freeze(id, date(1), date(21))
            val session = debits.session(date(1))
            debits.mark(session, client, "present")

            debits.reconcile(session, client)

            assertEquals(date(30), active(id).endDate)
            assertNull(active(id).freeze)
        }

    @Test
    fun `посещение до начала заморозки её не трогает`() =
        runTest {
            val client = fixture.insertClient()
            val id = november(client)
            freeze(id, date(10), date(20))
            fixture.at(date(8))
            val session = debits.session(date(8))
            debits.mark(session, client, "present")

            debits.reconcile(session, client)

            assertEquals(id, debits.debitedFrom(session, client))
            assertEquals(date(10), active(id).freeze?.start)
            assertEquals(date(11, 12), active(id).endDate)
        }

    @Test
    fun `возврат после разморозки посещением не восстанавливает заморозку, пропуск её не снимает`() =
        runTest {
            val client = fixture.insertClient()
            val id = november(client)
            freeze(id, date(1), date(21))
            fixture.at(date(15))
            val absent = debits.session(date(14))
            debits.mark(absent, client, "absent")
            debits.reconcile(absent, client)
            assertEquals(date(1), active(id).freeze?.start)

            val session = debits.session(date(15))
            debits.mark(session, client, "present")
            debits.reconcile(session, client)
            debits.mark(session, client, "absent")
            debits.reconcile(session, client)

            val after = active(id)
            assertNull(after.freeze)
            assertEquals(date(14, 12), after.endDate)
            assertEquals(8, after.sessionsRemaining)
        }

    @Test
    fun `незамороженный абонемент подбирается раньше замороженного`() =
        runTest {
            val client = fixture.insertClient()
            val frozen = november(client, name = "Замороженный")
            val other = november(client, name = "Обычный")
            debits.setTerm(other, date(1), date(31, 12))
            freeze(frozen, date(1), date(21))
            fixture.at(date(15))
            val session = debits.session(date(15))
            debits.mark(session, client, "present")

            debits.reconcile(session, client)

            assertEquals(other, debits.debitedFrom(session, client))
            assertEquals(date(1), active(frozen).freeze?.start)
        }
}
