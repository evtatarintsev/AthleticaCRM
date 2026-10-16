package org.athletica.crm.domain.memberships

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.SystemContexts
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.ActivationReason
import org.athletica.crm.core.subscription.CloseMode
import org.athletica.crm.core.subscription.CloseReason
import org.athletica.crm.domain.org.DbOrgCalendar
import org.athletica.crm.usecases.memberships.closeMembership
import org.athletica.crm.usecases.memberships.revokeMembership
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** DB-тесты хранения абонементов, выдачи в разных режимах и переходов между состояниями. */
class DbMembershipsTest {
    private val fixture = MembershipFixture()
    private val calendar = DbOrgCalendar(fixture.clock)

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking { fixture.setUp() }
    }

    @Test
    fun `выдача сразу — активный с датой начала выдачи и включительной датой окончания`() =
        runTest {
            val clientId = fixture.insertClient()
            val issued = fixture.issue(clientId)

            val active = assertIs<ActiveMembership>(fixture.reload(issued.id))
            assertEquals(LocalDate(2026, 10, 16), active.startDate)
            assertEquals(LocalDate(2026, 11, 15), active.endDate)
            assertEquals(8, active.sessionsRemaining)
            assertEquals(fixture.employeeId, active.issuedBy)
            assertEquals(LocalDate(2026, 10, 16), active.issuedOn)
            assertEquals(listOf("ISSUED", "ACTIVATED"), fixture.eventKinds(issued.id))
        }

    @Test
    fun `выдача с прошедшей даты активирует этой датой`() =
        runTest {
            val issued = fixture.issue(fixture.insertClient(), Activation.OnDate(LocalDate(2026, 10, 10)))

            assertEquals(LocalDate(2026, 10, 10), assertIs<ActiveMembership>(issued).startDate)
        }

    @Test
    fun `выдача с будущей даты — неактивирован без дат`() =
        runTest {
            val issued = fixture.issue(fixture.insertClient(), Activation.OnDate(LocalDate(2026, 10, 20)))

            val inactive = assertIs<InactiveMembership.OnDate>(fixture.reload(issued.id))
            assertEquals(LocalDate(2026, 10, 20), inactive.activation.date)
            assertEquals(listOf("ISSUED"), fixture.eventKinds(issued.id))
        }

    @Test
    fun `выдача при первом посещении хранит крайнюю дату`() =
        runTest {
            val issued = fixture.issue(fixture.insertClient(), Activation.OnFirstVisit(LocalDate(2026, 10, 25)))

            val inactive = assertIs<InactiveMembership.OnFirstVisit>(fixture.reload(issued.id))
            assertEquals(LocalDate(2026, 10, 25), inactive.activation.deadline)
        }

    @Test
    fun `безлимит хранится как NULL занятий`() =
        runTest {
            val issued = fixture.issue(fixture.insertClient(), sessions = null)

            val reloaded = fixture.reload(issued.id)
            assertNull(reloaded.sessionsTotal)
            assertNull(reloaded.sessionsRemaining)
        }

    @Test
    fun `системный контекст организации в USD отдаёт цену в USD`() =
        runTest {
            TestPostgres.truncate()
            val usd = MembershipFixture(currency = Currency.USD)
            usd.setUp()
            val issued = usd.issue(usd.insertClient())

            val price =
                usd.tx(usd.system) {
                    context(SystemContexts.forOrg(usd.orgId)) { usd.memberships.byId(issued.id).price }
                }
            assertEquals(Money(4_000_00, Currency.USD), price)
        }

    @Test
    fun `активация неактивированного`() =
        runTest {
            val issued = fixture.issue(fixture.insertClient(), Activation.OnFirstVisit(null))

            fixture.tx(fixture.system) {
                assertIs<InactiveMembership>(fixture.memberships.byId(issued.id)).activate(LocalDate(2026, 10, 18), ActivationReason.FIRST_VISIT)
            }

            val active = assertIs<ActiveMembership>(fixture.reload(issued.id))
            assertEquals(LocalDate(2026, 10, 18), active.startDate)
            assertEquals(LocalDate(2026, 11, 17), active.endDate)
        }

    @Test
    fun `закрытие активного`() =
        runTest {
            val issued = fixture.issue(fixture.insertClient())

            fixture.tx { closeMembership(fixture.memberships, calendar, issued.id) }

            val closed = assertIs<ClosedMembership>(fixture.reload(issued.id))
            assertEquals(CloseReason.MANUAL, closed.closeReason)
            assertEquals(LocalDate(2026, 10, 16), closed.closedOn)
            assertEquals(listOf("ISSUED", "ACTIVATED", "CLOSED"), fixture.eventKinds(issued.id))
        }

    @Test
    fun `закрытие неактивированного отклоняется`() =
        runTest {
            val issued = fixture.issue(fixture.insertClient(), Activation.OnFirstVisit(null))

            val result = fixture.attempt { closeMembership(fixture.memberships, calendar, issued.id) }

            assertEquals("MEMBERSHIP_NOT_ACTIVE", assertIs<Either.Left<DomainError>>(result).value.code)
            assertIs<InactiveMembership>(fixture.reload(issued.id))
        }

    @Test
    fun `отзыв неактивированного`() =
        runTest {
            val issued = fixture.issue(fixture.insertClient(), Activation.OnDate(LocalDate(2026, 10, 20)))

            fixture.tx { revokeMembership(fixture.memberships, issued.id) }

            val revoked = assertIs<RevokedMembership>(fixture.reload(issued.id))
            assertNull(revoked.startDate)
            assertEquals(listOf("ISSUED", "REVOKED"), fixture.eventKinds(issued.id))
        }

    @Test
    fun `отзыв активного сохраняет даты`() =
        runTest {
            val issued = fixture.issue(fixture.insertClient())

            fixture.tx { revokeMembership(fixture.memberships, issued.id) }

            assertEquals(LocalDate(2026, 10, 16), assertIs<RevokedMembership>(fixture.reload(issued.id)).startDate)
        }

    @Test
    fun `из закрытого переходов нет`() =
        runTest {
            val issued = fixture.issue(fixture.insertClient())
            fixture.tx { closeMembership(fixture.memberships, calendar, issued.id) }

            val revoke = fixture.attempt { revokeMembership(fixture.memberships, issued.id) }
            val close = fixture.attempt { closeMembership(fixture.memberships, calendar, issued.id) }

            assertEquals("MEMBERSHIP_FINAL", assertIs<Either.Left<DomainError>>(revoke).value.code)
            assertEquals("MEMBERSHIP_FINAL", assertIs<Either.Left<DomainError>>(close).value.code)
            assertIs<ClosedMembership>(fixture.reload(issued.id))
            assertEquals(listOf("ISSUED", "ACTIVATED", "CLOSED"), fixture.eventKinds(issued.id))
        }

    @Test
    fun `из отозванного переходов нет`() =
        runTest {
            val issued = fixture.issue(fixture.insertClient())
            fixture.tx { revokeMembership(fixture.memberships, issued.id) }

            val close = fixture.attempt { closeMembership(fixture.memberships, calendar, issued.id) }

            assertEquals("MEMBERSHIP_FINAL", assertIs<Either.Left<DomainError>>(close).value.code)
            assertIs<RevokedMembership>(fixture.reload(issued.id))
        }

    @Test
    fun `безлимит с закрытием по занятиям не выдаётся`() =
        runTest {
            val clientId = fixture.insertClient()
            val result =
                fixture.attempt {
                    fixture.memberships.issue(
                        org.athletica.crm.core.entityids.MembershipId.new(),
                        clientId,
                        null,
                        "Безлимит",
                        null,
                        listOf(fixture.groupId),
                        MembershipDuration(1, org.athletica.crm.core.subscription.DurationUnit.MONTHS),
                        Money(1_000_00, Currency.RUB),
                        Activation.Immediate,
                        CloseMode.ON_LAST_SESSION,
                        LocalDate(2026, 10, 16),
                    )
                }

            assertEquals("UNLIMITED_CLOSE_ON_LAST_SESSION", assertIs<Either.Left<DomainError>>(result).value.code)
            assertEquals(emptyList(), fixture.tx { fixture.memberships.forClient(clientId) })
        }

    @Test
    fun `выдача без групп отклоняется`() =
        runTest {
            val clientId = fixture.insertClient()

            val result = runCatchingIssue(clientId, emptyList())

            assertEquals("MEMBERSHIP_GROUPS_REQUIRED", assertIs<Either.Left<DomainError>>(result).value.code)
            assertEquals(emptyList(), fixture.tx { fixture.memberships.forClient(clientId) })
        }

    @Test
    fun `выдача с чужой группой отклоняется и не сохраняет абонемент`() =
        runTest {
            val other = MembershipFixture()
            other.setUp()
            val clientId = fixture.insertClient()

            val result = runCatchingIssue(clientId, listOf(fixture.groupId, other.groupId))

            assertEquals("GROUP_NOT_FOUND", assertIs<Either.Left<DomainError>>(result).value.code)
            assertEquals(emptyList(), fixture.tx { fixture.memberships.forClient(clientId) })
        }

    @Test
    fun `группы хранятся с абонементом и отдаются с названиями`() =
        runTest {
            val issued = fixture.issue(fixture.insertClient())

            assertEquals(listOf(MembershipGroup(fixture.groupId, "Дети 7–9")), fixture.reload(issued.id).groups)
        }

    /** Пытается выдать абонемент клиенту [clientId] с группами [groupIds]. */
    private suspend fun runCatchingIssue(clientId: org.athletica.crm.core.entityids.ClientId, groupIds: List<org.athletica.crm.core.entityids.GroupId>) =
        fixture.attempt {
            fixture.memberships.issue(
                org.athletica.crm.core.entityids.MembershipId.new(),
                clientId,
                null,
                "8 занятий",
                8,
                groupIds,
                MembershipDuration(1, org.athletica.crm.core.subscription.DurationUnit.MONTHS),
                Money(1_000_00, Currency.RUB),
                Activation.Immediate,
                CloseMode.MANUAL,
                LocalDate(2026, 10, 16),
            )
        }

    @Test
    fun `forClient возвращает только абонементы своей организации`() =
        runTest {
            val other = MembershipFixture()
            other.setUp()
            val clientId = fixture.insertClient()
            fixture.issue(clientId, name = "Свой")
            other.issue(other.insertClient(), name = "Чужой")

            assertEquals(listOf("Свой"), fixture.tx { fixture.memberships.forClient(clientId) }.map { it.name })
        }
}
