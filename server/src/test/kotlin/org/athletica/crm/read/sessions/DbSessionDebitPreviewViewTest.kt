package org.athletica.crm.read.sessions

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.attendance.DebitedParticipantDebitSchema
import org.athletica.crm.api.schemas.attendance.NoMembershipParticipantDebitSchema
import org.athletica.crm.api.schemas.attendance.NotDebitedParticipantDebitSchema
import org.athletica.crm.api.schemas.attendance.WillDebitParticipantDebitSchema
import org.athletica.crm.domain.memberships.Activation
import org.athletica.crm.domain.memberships.DebitFixture
import org.athletica.crm.domain.memberships.MembershipFixture
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** DB-тесты превью списаний для проведённого и запланированного занятия. */
class DbSessionDebitPreviewViewTest {
    private val fixture = MembershipFixture()
    private val debits = DebitFixture(fixture)
    private val view = DbSessionDebitPreviewView()

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            fixture.setUp()
            debits.setUp()
        }
    }

    private suspend fun stateOf(session: org.athletica.crm.core.entityids.SessionId, client: org.athletica.crm.core.entityids.ClientId) = fixture.tx { context(fixture.ctx) { view.preview(session) } }.participants.single { it.clientId == client }.debit

    @Test
    fun `проведённое занятие показывает фактическое списание и отсутствие`() =
        runTest {
            val withMembership = fixture.insertClient()
            val issued = fixture.issue(withMembership)
            val absent = fixture.insertClient()
            val unpaid = fixture.insertClient()
            val session = debits.session(LocalDate(2026, 10, 16))
            debits.mark(session, withMembership, "present")
            debits.mark(session, absent, "absent")
            debits.mark(session, unpaid, "present")
            debits.reconcile(session, withMembership)

            val debited = assertIs<DebitedParticipantDebitSchema>(stateOf(session, withMembership))
            assertEquals(issued.id, debited.membership.id)
            assertEquals(7, debited.membership.sessionsRemaining)
            assertEquals(NotDebitedParticipantDebitSchema, stateOf(session, absent))
            assertEquals(NoMembershipParticipantDebitSchema, stateOf(session, unpaid))
        }

    @Test
    fun `запланированное занятие показывает активацию неактивированного`() =
        runTest {
            val client = fixture.insertClient()
            fixture.issue(client, Activation.OnFirstVisit(null), issuedOn = LocalDate(2020, 1, 1))
            val session = debits.session(LocalDate(2020, 1, 10), status = "scheduled")
            debits.mark(session, client, null)

            val willDebit = assertIs<WillDebitParticipantDebitSchema>(stateOf(session, client))
            assertTrue(willDebit.activates)
        }
}
