package org.athletica.crm.usecases.memberships

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.memberships.MembershipExtendedEventSchema
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.domain.memberships.Activation
import org.athletica.crm.domain.memberships.ActiveMembership
import org.athletica.crm.domain.memberships.MembershipFixture
import org.athletica.crm.domain.org.DbOrgCalendar
import org.athletica.crm.read.memberships.DbMembershipHistoryView
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** DB-тесты ручного продления абонемента: проверки, история, аудит и эпизод уведомления «к закрытию». */
class MembershipExtensionTest {
    private val fixture = MembershipFixture()
    private val calendar = DbOrgCalendar(fixture.clock)

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking { fixture.setUp() }
    }

    private fun date(day: Int, month: Int = 11) = LocalDate(2026, month, day)

    /** Активный абонемент на ноябрь (01.11–30.11); сегодня 01.11. */
    private suspend fun november(client: ClientId): MembershipId {
        fixture.at(date(1))
        return fixture.issue(client, issuedOn = date(1), name = "Ноябрь").id
    }

    private suspend fun extend(
        id: MembershipId,
        endDate: LocalDate,
        reason: String = "перенос сборов",
    ) = fixture.attempt { extendMembership(fixture.memberships, calendar, id, endDate, reason) }

    private suspend fun errorCode(
        id: MembershipId,
        endDate: LocalDate,
        reason: String = "перенос сборов",
    ) = assertIs<CommonDomainError>(extend(id, endDate, reason).leftOrNull()).code

    private suspend fun active(id: MembershipId) = assertIs<ActiveMembership>(fixture.reload(id))

    /** Данные записей аудита абонемента [id] с автором, от старых к новым. */
    private suspend fun auditRows(id: MembershipId): List<Pair<String, String>> =
        TestPostgres.db
            .sql("SELECT username, data::text AS data FROM audit_logs WHERE entity_id = :id ORDER BY created_at, id")
            .bind("id", id)
            .list { it.get("username", String::class.java)!! to it.get("data", String::class.java)!! }

    @Test
    fun `продление меняет только дату окончания и пишется в историю и аудит`() =
        runTest {
            val id = november(fixture.insertClient())
            val before = active(id)

            extend(id, date(15, 12), "  перенос сборов  ")

            val extended = active(id)
            assertEquals(date(15, 12), extended.endDate)
            assertEquals(before.startDate, extended.startDate)
            assertEquals(before.sessionsRemaining, extended.sessionsRemaining)
            assertEquals("EXTENDED", fixture.eventKinds(id).last())
            val (author, data) = auditRows(id).last()
            assertEquals(fixture.ctx.username, author)
            assertTrue(data.contains("\"EXTENDED\""))
            assertTrue(data.contains("2026-11-30") && data.contains("2026-12-15"))
            assertTrue(data.contains("\"перенос сборов\""))
        }

    @Test
    fun `история показывает продление с обеими датами и обрезанной причиной`() =
        runTest {
            val id = november(fixture.insertClient())
            extend(id, date(15, 12), "  перенос сборов  ")

            val events = fixture.tx { context(fixture.ctx) { DbMembershipHistoryView().byMembership(id) } }.entries.map { it.event }

            assertEquals(MembershipExtendedEventSchema(date(30), date(15, 12), "перенос сборов"), events[0])
        }

    @Test
    fun `недопустимые продления отклоняются без изменений`() =
        runTest {
            val id = november(fixture.insertClient())
            val eventsBefore = fixture.eventKinds(id)
            val auditBefore = auditRows(id)

            assertEquals("EXTENSION_NOT_LATER", errorCode(id, date(30)))
            assertEquals("EXTENSION_NOT_LATER", errorCode(id, date(20)))
            assertEquals("EXTENSION_REASON_REQUIRED", errorCode(id, date(15, 12), ""))
            assertEquals("EXTENSION_REASON_REQUIRED", errorCode(id, date(15, 12), "   "))

            assertEquals(date(30), active(id).endDate)
            assertEquals(eventsBefore, fixture.eventKinds(id))
            assertEquals(auditBefore, auditRows(id))
        }

    @Test
    fun `замороженный абонемент не продлевается`() =
        runTest {
            val id = november(fixture.insertClient())
            fixture.tx { freezeMembership(fixture.memberships, calendar, id, date(10), date(12), "отпуск") }
            val frozenEnd = active(id).endDate

            assertEquals("MEMBERSHIP_FROZEN", errorCode(id, date(31, 12)))

            assertEquals(frozenEnd, active(id).endDate)
            assertEquals("FROZEN", fixture.eventKinds(id).last())
        }

    @Test
    fun `закончившаяся заморозка снимается перед продлением`() =
        runTest {
            val id = november(fixture.insertClient())
            fixture.tx { freezeMembership(fixture.memberships, calendar, id, date(1), date(3), "отпуск") }
            fixture.at(date(10))

            extend(id, date(31, 12))

            val extended = active(id)
            assertNull(extended.freeze)
            assertEquals(date(31, 12), extended.endDate)
            assertEquals(listOf("UNFROZEN", "EXTENDED"), fixture.eventKinds(id).takeLast(2))
        }

    @Test
    fun `неактивированный, закрытый и отозванный абонементы не продлеваются`() =
        runTest {
            fixture.at(date(1))
            val inactive = fixture.issue(fixture.insertClient(), Activation.OnFirstVisit(null), issuedOn = date(1)).id
            val closed = november(fixture.insertClient())
            fixture.tx { closeMembership(fixture.memberships, calendar, closed) }
            val revoked = november(fixture.insertClient())
            fixture.tx { revokeMembership(fixture.memberships, revoked) }

            assertEquals("MEMBERSHIP_NOT_ACTIVE", errorCode(inactive, date(31, 12)))
            assertEquals("MEMBERSHIP_FINAL", errorCode(closed, date(31, 12)))
            assertEquals("MEMBERSHIP_FINAL", errorCode(revoked, date(31, 12)))
            listOf(inactive, closed, revoked).forEach {
                assertTrue("EXTENDED" !in fixture.eventKinds(it))
            }
        }

    @Test
    fun `продление истёкшего абонемента закрывает эпизод уведомления`() =
        runTest {
            val id = november(fixture.insertClient())
            markCloseNotified(id)
            fixture.at(date(5, 12))

            extend(id, date(31, 12))

            val extended = active(id)
            assertNull(extended.closeNotifiedAt)
            assertTrue(!extended.statusAt(date(5, 12)).closePending)
        }

    @Test
    fun `продление абонемента без занятий сохраняет отметку уведомления`() =
        runTest {
            val id = november(fixture.insertClient())
            fixture.setRemaining(id, 0)
            markCloseNotified(id)
            fixture.at(date(5, 12))

            extend(id, date(31, 12))

            assertNotNull(active(id).closeNotifiedAt)
        }

    /** Отмечает уведомление «к закрытию» абонемента [id] как отправленное. */
    private suspend fun markCloseNotified(id: MembershipId) {
        TestPostgres.db
            .sql("UPDATE memberships SET close_notified_at = :at WHERE id = :id")
            .bind("at", Instant.parse("2026-12-01T09:00:00Z"))
            .bind("id", id)
            .execute()
    }
}
