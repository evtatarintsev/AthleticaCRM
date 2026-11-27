package org.athletica.crm.domain.ledger

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.finance.CashJournalRequest
import org.athletica.crm.api.schemas.finance.CashJournalResponse
import org.athletica.crm.api.schemas.finance.CashPaymentInput
import org.athletica.crm.api.schemas.finance.PaymentInput
import org.athletica.crm.api.schemas.memberships.IssueMembershipRequest
import org.athletica.crm.configureServer
import org.athletica.crm.core.Lang
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.entityids.CashAccountId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.core.finance.CashAccountKind
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.ActivationMode
import org.athletica.crm.core.subscription.CloseMode
import org.athletica.crm.core.subscription.DurationUnit
import org.athletica.crm.domain.auth.SignUp
import org.athletica.crm.domain.cashaccounts.CashAccounts
import org.athletica.crm.domain.casharticles.seedCashArticles
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.domain.memberships.MembershipFixture
import org.athletica.crm.employeeContext
import org.athletica.crm.sessionAccessToken
import org.athletica.crm.storage.asInt
import org.athletica.crm.testDi
import org.athletica.crm.testJwtConfig
import org.athletica.crm.testName
import org.athletica.crm.testPositiveInt
import org.athletica.crm.usecases.finance.ClientPayments
import org.dhatim.fastexcel.reader.CellType
import org.dhatim.fastexcel.reader.ReadableWorkbook
import org.junit.Before
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FinanceRoutesTest {
    private val fixture = MembershipFixture()
    private val cash = CashAccountId.new()
    private var clientId = ClientId.new()
    private val date = LocalDate(2026, 10, 16)

    private fun rub(amount: Long) = Money(amount * 100, Currency.RUB)

    private val ctx get() =
        employeeContext(
            orgId = fixture.orgId, userId = fixture.ctx.userId, employeeId = fixture.employeeId,
            permissions = EmployeePermission(emptyList(), org.athletica.crm.core.permissions.UserPermission.entries.toSet(), emptySet()),
        )

    @Before
    fun setUp() =
        runBlocking {
            TestPostgres.truncate()
            fixture.setUp()
            clientId = fixture.insertClient()
            orThrow {
                TestPostgres.db.transaction {
                    seedCashArticles(fixture.orgId, Lang.RU)
                    context(ctx) { CashAccounts().save(cash, "Cash", CashAccountKind.CASH, null, null, null) }
                }
            }
        }

    private fun token() =
        testJwtConfig.sessionAccessToken(
            object : AuthenticatedUser {
                override val id = fixture.ctx.userId
                override val orgId = fixture.orgId
                override val branchId = fixture.branchId
                override val employeeId = fixture.employeeId
                override val username = "finance@test"
            },
        )

    @Test
    fun `маршруты финансов отклоняют сотрудника без прав`() =
        testApplication {
            orThrow { TestPostgres.db.sql("UPDATE employees SET is_owner = false WHERE id = :id").bind("id", fixture.employeeId).execute() }
            val di = testDi()
            application { context(di) { configureServer() } }
            val response = client.post("/api/finance/cash-accounts") { header(HttpHeaders.Authorization, "Bearer ${token()}") }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("PERMISSION_DENIED"))
            val history =
                client.post("/api/finance/client-ledger") {
                    header(HttpHeaders.Authorization, "Bearer ${token()}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"clientId":"$clientId"}""")
                }
            assertEquals(HttpStatusCode.BadRequest, history.status)
        }

    @Test
    fun `выдача с переплатой атомарна и отказ не оставляет продажи`() =
        testApplication {
            val di = testDi()
            application { context(di) { configureServer() } }

            fun request(groups: List<org.athletica.crm.core.entityids.GroupId>, account: CashAccountId = cash) =
                IssueMembershipRequest(
                    id = MembershipId.new(), clientId = clientId, tariffPlanId = null, name = "Membership", sessions = testPositiveInt(8),
                    groupIds = groups, durationValue = testPositiveInt(1), durationUnit = DurationUnit.MONTHS, price = rub(4000),
                    activationMode = ActivationMode.IMMEDIATE, closeMode = CloseMode.MANUAL,
                    payment = PaymentInput(listOf(CashPaymentInput(account, rub(5000)))),
                )
            for (invalid in listOf(request(emptyList()), request(listOf(fixture.groupId), CashAccountId.new()))) {
                val response =
                    client.post("/api/memberships/issue") {
                        header(HttpHeaders.Authorization, "Bearer ${token()}")
                        contentType(ContentType.Application.Json)
                        setBody(Json.encodeToString(invalid))
                    }
                assertEquals(HttpStatusCode.BadRequest, response.status, response.bodyAsText())
                assertEquals(0, orThrow { TestPostgres.db.sql("SELECT COUNT(*)::int AS n FROM sales WHERE org_id = :org").bind("org", fixture.orgId).firstOrNull { it.asInt("n") } })
            }
            val response =
                client.post("/api/memberships/issue") {
                    header(HttpHeaders.Authorization, "Bearer ${token()}")
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(request(listOf(fixture.groupId))))
                }
            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            val summary = fixture.tx { context(ctx) { org.athletica.crm.read.finance.DbClientLedgerView().byClient(clientId) } }
            assertEquals(rub(1000), summary.balance)
            assertEquals(rub(0), summary.debt)
            assertEquals(rub(5000), fixture.tx { context(ctx) { org.athletica.crm.read.finance.DbCashAccountBalancesView().all().total } })
        }

    @Test
    fun `CSV выгружает все строки независимо от размера страницы`() =
        testApplication {
            fixture.tx {
                context(ctx) {
                    listOf("=1+1", " -1+1").forEach { comment -> ClientPayments().pay(null, clientId, PaymentInput(listOf(CashPaymentInput(cash, rub(1000)))), date, comment) }
                }
            }
            val di = testDi()
            application { context(di) { configureServer() } }
            val request = Json.encodeToString(CashJournalRequest(date, date, limit = 1))
            val page =
                client.post("/api/finance/journal") {
                    header(HttpHeaders.Authorization, "Bearer ${token()}")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
            val response = Json.decodeFromString<CashJournalResponse>(page.bodyAsText())
            assertEquals(1, response.entries.size)
            assertEquals(2, response.count)
            val export =
                client.post("/api/finance/journal/export") {
                    header(HttpHeaders.Authorization, "Bearer ${token()}")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
            assertEquals(HttpStatusCode.OK, export.status)
            assertEquals(3, export.bodyAsText().trim().lines().size)
            assertTrue(export.bodyAsText().contains("'=1+1"))
            assertTrue(export.bodyAsText().contains("' -1+1"))
            assertTrue(export.headers[HttpHeaders.ContentDisposition].orEmpty().contains("cash-journal.csv"))
            val report =
                client.post("/api/finance/cash-flow/export") {
                    header(HttpHeaders.Authorization, "Bearer ${token()}")
                    contentType(ContentType.Application.Json)
                    setBody("""{"from":"$date","to":"$date"}""")
                }
            assertEquals(HttpStatusCode.OK, report.status)
            assertTrue(report.bodyAsText().contains("Поступления от клиентов"))
            val book =
                client.post("/api/finance/journal/export?format=xlsx") {
                    header(HttpHeaders.Authorization, "Bearer ${token()}")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
            assertEquals(HttpStatusCode.OK, book.status)
            assertTrue(book.headers[HttpHeaders.ContentDisposition].orEmpty().contains("cash-journal.xlsx"))
            ReadableWorkbook(book.bodyAsBytes().inputStream()).use { workbook ->
                val rows = workbook.firstSheet.read()
                assertEquals(3, rows.size)
                val amount = rows[1].getCell(4)
                assertEquals(CellType.NUMBER, amount.type)
                assertEquals(0, BigDecimal("1000.00").compareTo(amount.asNumber()))
            }
        }

    @Test
    fun `регистрация создаёт системные и пользовательские статьи организации`() =
        runTest {
            orThrow {
                val user = TestPostgres.db.transaction { TestPostgres.auth.signup(SignUp(testName("Finance"), testName("Owner"), "finance@example.test", "password123", "UTC", Currency.RUB), Lang.EN, null) }
                val count = TestPostgres.db.sql("SELECT COUNT(*)::int AS n FROM cash_articles WHERE org_id = :org").bind("org", user.orgId).firstOrNull { it.asInt("n") }
                assertEquals(10, count)
                assertEquals(2, TestPostgres.db.sql("SELECT COUNT(*)::int AS n FROM cash_articles WHERE org_id = :org AND system_code IS NOT NULL").bind("org", user.orgId).firstOrNull { it.asInt("n") })
            }
        }
}
