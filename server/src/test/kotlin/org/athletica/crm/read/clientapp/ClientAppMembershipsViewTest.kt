package org.athletica.crm.read.clientapp

import arrow.core.Either
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.clientapp.ClientAppMembershipFreezeSchema
import org.athletica.crm.api.schemas.clientapp.ClientAppMembershipState
import org.athletica.crm.api.schemas.clientapp.ClientAppMembershipsResponse
import org.athletica.crm.clientapp.ClientAppFixture
import org.athletica.crm.core.ClientAccountContext
import org.athletica.crm.core.ClientProfileContext
import org.athletica.crm.core.ClientProfileContexts
import org.athletica.crm.core.clientapp.ClientAppAccessStatus
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.EndingSoonReason
import org.athletica.crm.domain.memberships.Activation
import org.athletica.crm.domain.memberships.DebitFixture
import org.athletica.crm.domain.memberships.MembershipFixture
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/** DB-тесты проекции абонементов кабинета клиента. */
class ClientAppMembershipsViewTest {
    private val mom = "+7 903 123-45-67"
    private val fixture = MembershipFixture(membershipPrice = Money.zero(Currency.RUB))
    private val debits = DebitFixture(fixture)
    private val view = DbClientAppMembershipsView()

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            fixture.setUp()
            debits.setUp()
            orThrow { TestPostgres.db.sql("UPDATE organizations SET client_app_enabled = true WHERE id = :id").bind("id", fixture.orgId).execute() }
        }
    }

    /** Подключённый к аккаунту мамы клиент организации фикстуры с телефоном [phone]. */
    private suspend fun connected(phone: String = mom): ClientId = orThrow { ClientAppFixture.client(fixture.orgId, phone, ClientAppAccessStatus.CONNECTED) }

    /** Контекст нового аккаунта мамы. */
    private suspend fun momContext(): ClientAccountContext = orThrow { ClientAppFixture.accountContext(ClientAppFixture.account(mom)) }

    /** Абонементы клиента [clientId] глазами аккаунта [ctx] на момент часов фикстуры. */
    private suspend fun memberships(clientId: ClientId, ctx: ClientAccountContext? = null): ClientAppMembershipsResponse =
        orThrow {
            val account = ctx ?: momContext()
            TestPostgres.db.transaction {
                context(account) { context(ClientProfileContexts.profile(fixture.orgId, clientId)) { view.memberships(ClientAppMembershipsQuery(fixture.clock.now())) } }
            }
        }

    /** Списание занятия от [date] с абонемента [membershipId] клиента [clientId]; [reversed] — уже возвращено. */
    private suspend fun debit(
        membershipId: MembershipId,
        clientId: ClientId,
        date: LocalDate,
        reversed: Boolean = false,
    ): SessionId {
        val sessionId = debits.session(date)
        orThrow {
            TestPostgres.db.sql(
                """
                INSERT INTO membership_debits (org_id, membership_id, client_id, session_id, kind, reversed_at)
                VALUES (:org, :m, :c, :s, 'REGULAR', CASE WHEN CAST(:reversed AS boolean) THEN now() END)
                """.trimIndent(),
            ).bind("org", fixture.orgId).bind("m", membershipId).bind("c", clientId).bind("s", sessionId).bind("reversed", reversed).execute()
        }
        return sessionId
    }

    @Test
    fun `активный — остаток, срок, заканчивается и действующие списания`() =
        runTest {
            val ivan = connected()
            val id = fixture.issue(ivan).id
            fixture.setRemaining(id, 3)
            debits.setTerm(id, LocalDate(2026, 9, 21), LocalDate(2026, 10, 20))
            val first = debit(id, ivan, LocalDate(2026, 10, 1))
            val second = debit(id, ivan, LocalDate(2026, 10, 8))
            debit(id, ivan, LocalDate(2026, 10, 12), reversed = true)

            val membership = memberships(ivan).memberships.single()

            assertEquals(ClientAppMembershipState.ACTIVE, membership.state)
            assertEquals(8 to 3, membership.sessionsTotal to membership.sessionsRemaining)
            assertEquals(LocalDate(2026, 10, 20), membership.endDate)
            assertEquals(listOf(EndingSoonReason.BY_DATE), membership.endingSoon)
            assertFalse(membership.ended)
            assertEquals(listOf(second, first), membership.debits.map { it.sessionId })
            assertEquals("Дети 7–9", membership.debits.first().groupName)
        }

    @Test
    fun `заморозка без причины, неактивированный и история по порядку`() =
        runTest {
            val ivan = connected()
            val closed = fixture.issue(ivan, issuedOn = LocalDate(2026, 8, 1), name = "Старый").id
            val frozen = fixture.issue(ivan, name = "Текущий").id
            val next = fixture.issue(ivan, activation = Activation.OnDate(LocalDate(2026, 11, 20)), name = "Следующий").id
            orThrow {
                TestPostgres.db.sql("UPDATE memberships SET state = 'CLOSED', closed_on = '2026-09-01', close_reason = 'MANUAL' WHERE id = :id").bind("id", closed).execute()
                TestPostgres.db.sql("UPDATE memberships SET freeze_start = '2026-11-10', freeze_end = '2026-11-20', freeze_reason = 'травма колена' WHERE id = :id").bind("id", frozen).execute()
            }

            val response = memberships(ivan)

            assertEquals(listOf(frozen, next, closed), response.memberships.map { it.id })
            assertEquals(ClientAppMembershipFreezeSchema(LocalDate(2026, 11, 10), LocalDate(2026, 11, 20)), response.memberships[0].freeze)
            assertEquals(LocalDate(2026, 11, 20), response.memberships[1].activateOn)
            assertEquals(ClientAppMembershipState.CLOSED to LocalDate(2026, 9, 1), response.memberships[2].state to response.memberships[2].closedOn)
            assertFalse(response.toString().contains("травма"))
        }

    @Test
    fun `активный с вышедшим сроком или без занятий — закончился, прошедшая заморозка не отдаётся`() =
        runTest {
            val ivan = connected()
            val expired = fixture.issue(ivan, name = "Срок вышел").id
            debits.setTerm(expired, LocalDate(2026, 9, 1), LocalDate(2026, 10, 10))
            val exhausted = fixture.issue(ivan, name = "Без занятий").id
            debits.setTerm(exhausted, LocalDate(2026, 10, 1), LocalDate(2026, 10, 31))
            fixture.setRemaining(exhausted, 0)
            orThrow {
                TestPostgres.db.sql("UPDATE memberships SET freeze_start = '2026-10-01', freeze_end = '2026-10-15', freeze_reason = 'отпуск' WHERE id = :id").bind("id", exhausted).execute()
            }

            val byId = memberships(ivan).memberships.associateBy { it.id }

            assertEquals(true to emptyList(), byId.getValue(expired).ended to byId.getValue(expired).endingSoon)
            assertEquals(true to null, byId.getValue(exhausted).ended to byId.getValue(exhausted).freeze)
        }

    @Test
    fun `чужой клиент — CLIENT_NOT_FOUND, его абонементов не видно`() =
        runTest {
            val ivan = connected()
            val other = connected("+7 999 000-00-00")
            fixture.issue(other)

            val ctx = momContext()
            val forged = ClientProfileContext(ctx.accountId, ctx.sessionId, fixture.orgId, other, Currency.RUB, ctx.lang)
            val stranger = either { TestPostgres.db.transaction { context(forged) { view.memberships(ClientAppMembershipsQuery(fixture.clock.now())) } } }

            assertEquals("CLIENT_NOT_FOUND", assertIs<Either.Left<AppError>>(stranger).value.code)
            assertEquals(emptyList(), memberships(ivan, ctx).memberships)
        }
}
