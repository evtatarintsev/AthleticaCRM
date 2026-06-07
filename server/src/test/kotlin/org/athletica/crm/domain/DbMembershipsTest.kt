package org.athletica.crm.domain

import arrow.core.getOrElse
import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.DurationUnit
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.domain.memberships.DbMemberships
import org.athletica.crm.storage.Transaction
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

class DbMembershipsTest {
    private val orgId = OrgId.new()
    private val otherOrgId = OrgId.new()
    private val employeeId = EmployeeId.new()
    private val otherEmployeeId = EmployeeId.new()

    private val ctx = ctxFor(orgId, employeeId)
    private val otherCtx = ctxFor(otherOrgId, otherEmployeeId)

    private lateinit var memberships: DbMemberships

    private fun ctxFor(orgId: OrgId, employeeId: EmployeeId) =
        EmployeeRequestContext(
            lang = Lang.EN,
            orgId = orgId,
            currency = Currency.RUB,
            userId = UserId.new(),
            branchId = BranchId.new(),
            employeeId = employeeId,
            username = "user@example.com",
            clientIp = "127.0.0.1",
            permission = EmployeePermission(),
        )

    @Before
    fun setUp() {
        TestPostgres.truncate()
        memberships = DbMemberships()
        runBlocking {
            insertOrg(orgId, "Org 1", employeeId)
            insertOrg(otherOrgId, "Org 2", otherEmployeeId)
        }
    }

    private suspend fun insertOrg(orgId: OrgId, name: String, employeeId: EmployeeId) {
        TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
            .bind("id", orgId).bind("name", name).execute()
        TestPostgres.db.sql("INSERT INTO employees (id, org_id, name, is_owner) VALUES (:id, :orgId, :name, true)")
            .bind("id", employeeId).bind("orgId", orgId).bind("name", "Admin").execute()
    }

    private suspend fun insertClient(orgId: OrgId = this.orgId, name: String = "Иван Петров"): ClientId {
        val clientId = ClientId.new()
        TestPostgres.db
            .sql("INSERT INTO clients (id, org_id, name, gender) VALUES (:id, :orgId, :name, 'MALE'::gender)")
            .bind("id", clientId).bind("orgId", orgId).bind("name", name).execute()
        return clientId
    }

    /** Выдаёт и сохраняет абонемент с параметрами по умолчанию. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    private suspend fun issueDefault(
        clientId: ClientId,
        id: MembershipId = MembershipId.new(),
        name: String = "8 занятий",
        sessions: Int? = 8,
        durationValue: Int = 1,
        durationUnit: DurationUnit = DurationUnit.MONTHS,
        startDate: LocalDate = LocalDate(2026, 1, 10),
        price: Money = Money(4_000_00, Currency.RUB),
    ): MembershipId {
        memberships.new(id, clientId, null, name, sessions, durationValue, durationUnit, startDate, price).save()
        return id
    }

    @Test
    fun `issue сохраняет абонемент с остатком равным total и вычисленной датой окончания`() =
        runTest {
            either {
                val clientId = TestPostgres.db.transaction { context(ctx) { insertClient() } }
                val id = TestPostgres.db.transaction { context(ctx) { issueDefault(clientId) } }

                val list = TestPostgres.db.transaction { context(ctx) { memberships.forClient(clientId) } }
                assertEquals(1, list.size)
                val actual = list.first()
                assertEquals(id, actual.id)
                assertEquals(clientId, actual.clientId)
                assertEquals("8 занятий", actual.name)
                assertEquals(8, actual.sessionsTotal)
                assertEquals(8, actual.sessionsRemaining)
                assertEquals(LocalDate(2026, 1, 10), actual.startDate)
                assertEquals(LocalDate(2026, 2, 10), actual.endDate)
                assertEquals(Money(4_000_00, Currency.RUB), actual.price)
                assertEquals(employeeId, actual.issuedBy)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `issue сохраняет безлимит как NULL занятий`() =
        runTest {
            either {
                val clientId = TestPostgres.db.transaction { context(ctx) { insertClient() } }
                TestPostgres.db.transaction { context(ctx) { issueDefault(clientId, name = "Безлимит", sessions = null) } }

                val actual = TestPostgres.db.transaction { context(ctx) { memberships.forClient(clientId) } }.first()
                assertNull(actual.sessionsTotal)
                assertNull(actual.sessionsRemaining)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `forClient возвращает только абонементы своей организации`() =
        runTest {
            either {
                val clientId = TestPostgres.db.transaction { context(ctx) { insertClient() } }
                val otherClientId = TestPostgres.db.transaction { context(otherCtx) { insertClient(orgId = otherOrgId) } }
                TestPostgres.db.transaction { context(ctx) { issueDefault(clientId, name = "Свой") } }
                TestPostgres.db.transaction { context(otherCtx) { issueDefault(otherClientId, name = "Чужой") } }

                val mine = TestPostgres.db.transaction { context(ctx) { memberships.forClient(clientId) } }
                assertEquals(listOf("Свой"), mine.map { it.name })
            }.getOrElse { fail("Unexpected error: $it") }
        }
}
