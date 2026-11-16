package org.athletica.crm.read.memberships

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.memberships.UnpaidSessionsResponse
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.domain.memberships.DebitFixture
import org.athletica.crm.domain.memberships.MembershipFixture
import org.athletica.crm.employeeContext
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
            val earlier = debits.session(LocalDate(2026, 10, 13), groupId = group(first.branchId))
            val absent = debits.session(LocalDate(2026, 10, 14))
            val scheduled = debits.session(LocalDate(2026, 10, 16), status = "scheduled")
            val cancelled = debits.session(LocalDate(2026, 10, 17), status = "cancelled")
            val foreign = foreignDebits.session(LocalDate(2026, 10, 12))
            listOf(later, earlier, scheduled, cancelled, foreign).forEach { debits.mark(it, client, "present") }
            debits.mark(absent, client, "absent")
            assertEquals(listOf(earlier, later), unpaid(client).sessions.map { it.sessionId })
            listOf(ClientId.new(), second.insertClient()).forEach {
                assertEquals("CLIENT_NOT_FOUND", assertIs<Either.Left<AppError>>(attempt(it)).value.code)
            }
        }

    @Test
    fun `sessions of another branch are hidden`() =
        runTest {
            val client = first.insertClient()
            val other = BranchId.new()
            first.tx {
                TestPostgres.db.sql("INSERT INTO branches (id, org_id, name) VALUES (:id, :org, 'Другой')")
                    .bind("id", other).bind("org", first.orgId).execute()
            }
            val local = debits.session(LocalDate(2026, 10, 13))
            val remote = debits.session(LocalDate(2026, 10, 14), groupId = group(other))
            listOf(local, remote).forEach { debits.mark(it, client, "present") }
            assertEquals(listOf(local), unpaid(client).sessions.map { it.sessionId })
        }

    @Test
    fun `debit excludes repayment clears and reversal restores`() =
        runTest {
            val client = first.insertClient()
            val session = debits.session(LocalDate(2026, 10, 15))
            debits.mark(session, client, "present")
            val membership = first.issue(client, issuedOn = LocalDate(2026, 10, 14))
            first.tx { first.memberships.repayDebt(membership.id, listOf(session), LocalDate(2026, 10, 16)) }
            assertEquals(emptyList(), unpaid(client).sessions)
            debits.mark(session, client, "absent")
            debits.reconcile(session, client)
            debits.mark(session, client, "present")
            assertEquals(listOf(session), unpaid(client).sessions.map { it.sessionId })
            debits.reconcile(session, client)
            assertEquals(emptyList(), unpaid(client).sessions)
        }

    /** Создаёт группу первой организации в филиале [branchId]. */
    private suspend fun group(branchId: BranchId): GroupId {
        val group = GroupId.new()
        first.tx {
            TestPostgres.db.sql("INSERT INTO groups (id, org_id, branch_id, name) VALUES (:id, :org, :branch, :name)")
                .bind("id", group).bind("org", first.orgId).bind("branch", branchId).bind("name", "Группа $group").execute()
        }
        return group
    }

    /** Неоплаченные посещения клиента [clientId] глазами сотрудника филиала первой организации. */
    private suspend fun unpaid(clientId: ClientId): UnpaidSessionsResponse = assertIs<Either.Right<UnpaidSessionsResponse>>(attempt(clientId)).value

    /** Запрос проекции для клиента [clientId] в контексте сотрудника филиала первой организации. */
    private suspend fun attempt(clientId: ClientId): Either<AppError, UnpaidSessionsResponse> {
        val context = employeeContext(orgId = first.orgId, branchId = first.branchId, employeeId = first.employeeId)
        return either {
            val raise: Raise<AppError> = this
            TestPostgres.db.transaction {
                context(context, this, raise) { view.byClient(clientId) }
            }
        }
    }
}
