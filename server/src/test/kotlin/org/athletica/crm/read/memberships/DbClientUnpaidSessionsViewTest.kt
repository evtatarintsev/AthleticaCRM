package org.athletica.crm.read.memberships

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.domain.memberships.DebitFixture
import org.athletica.crm.domain.memberships.MembershipFixture
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DbClientUnpaidSessionsViewTest {
    private val first = MembershipFixture()
    private val second = MembershipFixture()
    private val debits = DebitFixture(first)
    private val foreignDebits = DebitFixture(second)
    private val view = DbUnpaidSessionsView()

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            first.setUp()
            second.setUp()
            debits.setUp()
            foreignDebits.setUp()
        }
    }

    @Test
    fun `all groups without membership or current enrollment sorted and scoped`() =
        runTest {
            val client = first.insertClient()
            val later = debits.session(LocalDate(2026, 10, 15))
            val earlier = debits.session(LocalDate(2026, 10, 13), groupId = secondGroup())
            val absent = debits.session(LocalDate(2026, 10, 14))
            val scheduled = debits.session(LocalDate(2026, 10, 16), status = "scheduled")
            val cancelled = debits.session(LocalDate(2026, 10, 17), status = "cancelled")
            val foreign = foreignDebits.session(LocalDate(2026, 10, 12))
            listOf(later, earlier, scheduled, cancelled, foreign).forEach { debits.mark(it, client, "present") }
            debits.mark(absent, client, "absent")
            assertEquals(listOf(earlier, later), first.tx { view.byClient(client) }.sessions.map { it.sessionId })
            for (id in listOf(ClientId.new(), second.insertClient())) {
                assertEquals("CLIENT_NOT_FOUND", assertIs<Either.Left<org.athletica.crm.core.errors.AppError>>(first.attempt { view.byClient(id) }).value.code)
            }
        }

    private suspend fun secondGroup(): org.athletica.crm.core.entityids.GroupId {
        val group = org.athletica.crm.core.entityids.GroupId.new()
        first.tx {
            TestPostgres.db.sql("INSERT INTO groups (id, org_id, branch_id, name) VALUES (:id, :org, :branch, 'Other')")
                .bind("id", group).bind("org", first.orgId).bind("branch", first.branchId).execute()
        }
        return group
    }

    @Test
    fun `debit excludes repayment clears and reversal restores`() =
        runTest {
            val client = first.insertClient()
            val session = debits.session(LocalDate(2026, 10, 15))
            debits.mark(session, client, "present")
            val membership = first.issue(client, issuedOn = LocalDate(2026, 10, 14))
            first.tx { first.memberships.repayDebt(membership.id, listOf(session), LocalDate(2026, 10, 16)) }
            assertEquals(emptyList(), first.tx { view.byClient(client) }.sessions)
            debits.mark(session, client, "absent")
            debits.reconcile(session, client)
            debits.mark(session, client, "present")
            assertEquals(listOf(session), first.tx { view.byClient(client) }.sessions.map { it.sessionId })
            debits.reconcile(session, client)
            assertEquals(emptyList(), first.tx { view.byClient(client) }.sessions)
        }
}
