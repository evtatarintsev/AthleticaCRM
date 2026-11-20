package org.athletica.crm.domain.ledger

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.finance.CashFlowReportRequest
import org.athletica.crm.api.schemas.finance.CashJournalRequest
import org.athletica.crm.api.schemas.finance.CashOperationRequest
import org.athletica.crm.api.schemas.finance.CashPaymentInput
import org.athletica.crm.api.schemas.finance.CreateSaleRequest
import org.athletica.crm.api.schemas.finance.PaymentInput
import org.athletica.crm.api.schemas.finance.SaleItemInput
import org.athletica.crm.api.schemas.finance.TransferCashRequest
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.CashAccountId
import org.athletica.crm.core.entityids.CashArticleId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.SaleId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.finance.CashAccountKind
import org.athletica.crm.core.finance.CashDirection
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.domain.cashaccounts.CashAccounts
import org.athletica.crm.domain.casharticles.CashArticles
import org.athletica.crm.domain.casharticles.seedCashArticles
import org.athletica.crm.domain.clients.DbClients
import org.athletica.crm.domain.clients.activeById
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.domain.memberships.MembershipFixture
import org.athletica.crm.domain.org.DbOrgCalendar
import org.athletica.crm.domain.sales.Sales
import org.athletica.crm.employeeContext
import org.athletica.crm.read.finance.DbCashAccountBalancesView
import org.athletica.crm.read.finance.DbCashArticlesView
import org.athletica.crm.read.finance.DbCashFlowReportView
import org.athletica.crm.read.finance.DbCashJournalView
import org.athletica.crm.read.finance.DbClientLedgerView
import org.athletica.crm.read.finance.DbSaleDetailView
import org.athletica.crm.storage.Transaction
import org.athletica.crm.usecases.finance.CashOperations
import org.athletica.crm.usecases.finance.ClientPayments
import org.athletica.crm.usecases.finance.SaleActions
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/** Денежные сценарии проходят реальные транзакции и читаются независимыми проекциями. */
class FinanceTest {
    private val fixture = MembershipFixture()
    private val calendar = DbOrgCalendar(fixture.clock)
    private val ledger = Ledger(calendar)
    private val accounts = CashAccounts(ledger, calendar)
    private val articles = CashArticles()
    private val sales = Sales(ledger, calendar)
    private val clients = DbClients()
    private val payments = ClientPayments(ledger, accounts, articles, sales, clients, calendar)
    private val actions = SaleActions(ledger, sales, payments, calendar)
    private val operations = CashOperations(ledger, accounts, articles)
    private lateinit var ctx: EmployeeRequestContext
    private var client = ClientId.new()
    private val cash = CashAccountId.new()
    private val bank = CashAccountId.new()

    private fun rub(amount: Long) = Money(amount * 100, Currency.RUB)

    private val today = LocalDate(2026, 10, 16)

    @Before
    fun setUp() =
        runBlocking {
            TestPostgres.truncate()
            fixture.setUp()
            ctx =
                employeeContext(
                    orgId = fixture.orgId, employeeId = fixture.employeeId, userId = fixture.ctx.userId,
                    permissions = EmployeePermission(emptyList(), UserPermission.entries.toSet(), emptySet()),
                )
            client = fixture.insertClient()
            tx {
                seedCashArticles(fixture.orgId, Lang.RU)
                accounts.save(cash, "Cash", CashAccountKind.CASH, null, null, null)
                accounts.save(bank, "Bank", CashAccountKind.BANK, null, null, null)
            }
        }

    private suspend fun <T> attempt(actor: EmployeeRequestContext = ctx, block: suspend context(EmployeeRequestContext, Transaction, Raise<AppError>) () -> T): Either<AppError, T> =
        either {
            val raise = contextOf<Raise<AppError>>()
            TestPostgres.db.transaction { block(actor, this, raise) }
        }

    private suspend fun <T> tx(block: suspend context(EmployeeRequestContext, Transaction, Raise<AppError>) () -> T): T = attempt(block = block).getOrElse { fail(it.toString()) }

    private suspend fun sale(amount: Long): SaleId {
        val id = SaleId.new()
        tx { actions.create(CreateSaleRequest(id, client, today, listOf(SaleItemInput("Service", 1, rub(amount))))) }
        return id
    }

    private fun cashPayment(amount: Long) = PaymentInput(listOf(CashPaymentInput(cash, rub(amount))))

    @Test
    fun `касса с остатком архив восстановление и занятое имя`() =
        runTest {
            val safe = CashAccountId.new()
            tx { accounts.save(safe, "Safe", CashAccountKind.CASH, fixture.branchId, rub(15000), today) }
            assertEquals(rub(15000), tx { DbCashAccountBalancesView().all() }.accounts.single { it.id == safe }.balance)
            assertIs<Either.Left<AppError>>(attempt { accounts.save(CashAccountId.new(), "Safe", CashAccountKind.CASH, null, null, null) })
            tx { accounts.archive(safe, true) }
            assertIs<Either.Left<AppError>>(attempt { accounts.activeById(safe) })
            tx { accounts.archive(safe, false) }
            assertFalse(tx { accounts.byId(safe) }.archived)
        }

    @Test
    fun `статьи два уровня архив и системные статьи`() =
        runTest {
            val group = CashArticleId.new()
            val child = CashArticleId.new()
            tx {
                articles.save(group, "Space", CashDirection.EXPENSE, null, true)
                articles.save(child, "Rent", CashDirection.EXPENSE, group, false)
            }
            assertIs<Either.Left<AppError>>(attempt { articles.save(CashArticleId.new(), "Third", CashDirection.EXPENSE, child, false) })
            assertIs<Either.Left<AppError>>(attempt { articles.save(CashArticleId.new(), "Income", CashDirection.INCOME, group, false) })
            assertIs<Either.Left<AppError>>(attempt { articles.archive(group, true) })
            val system = tx { articles.system("client_receipts") }
            assertIs<Either.Left<AppError>>(attempt { articles.save(system, "Changed", CashDirection.INCOME, null, false) })
            assertIs<Either.Left<AppError>>(attempt { articles.archive(system, true) })
            tx {
                articles.archive(child, true)
                articles.archive(group, true)
            }
            assertEquals(12, tx { DbCashArticlesView().all() }.articles.size)
        }

    @Test
    fun `продажа смешанная оплата переплата и история`() =
        runTest {
            val id = sale(4500)
            assertEquals(rub(4500), tx { DbSaleDetailView().byId(id) }.debt)
            tx { payments.pay(id, client, PaymentInput(listOf(CashPaymentInput(cash, rub(2000)), CashPaymentInput(bank, rub(3000)))), today) }
            val detail = tx { DbSaleDetailView().byId(id) }
            assertEquals(rub(4500), detail.paid)
            assertEquals(rub(0), detail.debt)
            val history = tx { DbClientLedgerView().byClient(client) }
            assertEquals(rub(500), history.balance)
            assertEquals(rub(500), history.advance)
            assertEquals(listOf(rub(5000), rub(-4500)), history.entries.map { it.amount })
            assertEquals(rub(500), history.entries.first().balanceAfter)
            assertEquals(rub(5000), tx { DbCashAccountBalancesView().all() }.total)
        }

    @Test
    fun `бесплатная продажа и пустые позиции`() =
        runTest {
            val id = sale(0)
            assertEquals(rub(0), tx { DbSaleDetailView().byId(id) }.debt)
            assertTrue(tx { DbClientLedgerView().byClient(client) }.entries.isEmpty())
            assertIs<Either.Left<AppError>>(attempt { actions.create(CreateSaleRequest(SaleId.new(), client, today, emptyList())) })
        }

    @Test
    fun `пополнение аванса оплата с аванса и корректировка в минус`() =
        runTest {
            tx { payments.pay(null, client, cashPayment(1000), today) }
            val id = sale(800)
            tx { payments.pay(id, client, PaymentInput(fromAdvance = rub(800)), today) }
            assertEquals(rub(200), tx { DbClientLedgerView().byClient(client) }.advance)
            assertEquals(rub(0), tx { DbClientLedgerView().byClient(client) }.entries.first().amount)
            tx { payments.adjust(clients.activeById(client), rub(-500), "Old debt") }
            assertEquals(rub(-300), tx { DbClientLedgerView().byClient(client) }.balance)
            assertIs<Either.Left<AppError>>(attempt { payments.adjust(clients.activeById(client), rub(1), " ") })
        }

    @Test
    fun `две одновременные оплаты с аванса не списывают больше остатка`() =
        runTest {
            tx { payments.pay(null, client, cashPayment(1000), today) }
            val first = sale(800)
            val second = sale(800)
            val gate = CompletableDeferred<Unit>()
            val a =
                async {
                    gate.await()
                    attempt { payments.pay(first, client, PaymentInput(fromAdvance = rub(800)), today) }
                }
            val b =
                async {
                    gate.await()
                    attempt { payments.pay(second, client, PaymentInput(fromAdvance = rub(800)), today) }
                }
            gate.complete(Unit)
            val results = listOf(a.await(), b.await())
            assertEquals(1, results.count { it is Either.Right })
            assertEquals(1, results.count { it is Either.Left })
            assertEquals(rub(200), tx { DbClientLedgerView().byClient(client) }.advance)
        }

    @Test
    fun `возврат сначала гасит долг затем зачисляет аванс и выплачивает`() =
        runTest {
            val id = sale(4500)
            tx { payments.pay(id, client, cashPayment(2000), today) }
            tx { actions.returnSale(id, rub(3000), "Return", null) }
            assertEquals(rub(0), tx { DbSaleDetailView().byId(id) }.debt)
            assertEquals(rub(500), tx { DbClientLedgerView().byClient(client) }.advance)
            assertIs<Either.Left<AppError>>(attempt { actions.returnSale(id, rub(2000), "Too much", null) })
            tx { actions.returnSale(id, rub(1000), "Return and payout", cash) }
            assertEquals(rub(500), tx { DbClientLedgerView().byClient(client) }.advance)
            assertEquals(rub(1000), tx { DbCashAccountBalancesView().all() }.total)
            assertIs<Either.Left<AppError>>(attempt { payments.payout(client, cash, rub(800), today) })
        }

    @Test
    fun `отмена продажи сторнирует оплаты и не позволяет потратить переплату дважды`() =
        runTest {
            val id = sale(4500)
            tx { payments.pay(id, client, cashPayment(5000), today) }
            val other = sale(500)
            tx { payments.pay(other, client, PaymentInput(fromAdvance = rub(500)), today) }
            assertIs<Either.Left<AppError>>(attempt { actions.cancel(id, "Error", fixture.memberships) })
            tx { payments.pay(null, client, cashPayment(500), today) }
            tx { actions.cancel(id, "Error", fixture.memberships) }
            assertEquals(rub(0), tx { DbSaleDetailView().byId(id) }.debt)
            assertTrue(tx { DbSaleDetailView().byId(id) }.cancelledAt != null)
            assertIs<Either.Left<AppError>>(attempt { payments.pay(id, client, cashPayment(100), today) })
        }

    @Test
    fun `расход перевод журнал и ДДС сходятся включая сторно в другом месяце`() =
        runTest {
            val rent = tx { DbCashArticlesView().all() }.articles.single { it.name == "Аренда" }.id
            tx { accounts.save(CashAccountId.new(), "Safe", CashAccountKind.CASH, null, rub(10000), LocalDate(2026, 10, 1)) }
            val expense = tx { operations.record(CashOperationRequest(cash, rent, rub(3000), today), CashDirection.EXPENSE) }
            tx { operations.transfer(TransferCashRequest(cash, bank, rub(2000), today)) }
            val journal = tx { DbCashJournalView().page(CashJournalRequest(LocalDate(2026, 10, 1), LocalDate(2026, 10, 31))) }
            assertEquals(3, journal.count)
            assertEquals(rub(3000), journal.expenses)
            val october = tx { DbCashFlowReportView().report(CashFlowReportRequest(LocalDate(2026, 10, 1), LocalDate(2026, 10, 31))) }
            assertEquals(rub(7000), october.closing)
            assertEquals(rub(3000), october.expenses)
            fixture.at(LocalDate(2026, 11, 1))
            tx { operations.reverse(expense, "Duplicate") }
            val historical = tx { DbCashFlowReportView().report(CashFlowReportRequest(LocalDate(2026, 10, 1), LocalDate(2026, 10, 31))) }
            assertEquals(october.closing, historical.closing)
            val november = tx { DbCashFlowReportView().report(CashFlowReportRequest(LocalDate(2026, 11, 1), LocalDate(2026, 11, 30))) }
            assertEquals(rub(7000), november.opening)
            assertEquals(rub(10000), november.closing)
            assertEquals(rub(-3000), november.expenses)
            val updated = tx { DbCashJournalView().page(CashJournalRequest(LocalDate(2026, 10, 1), LocalDate(2026, 10, 31))) }
            assertEquals(rub(0), updated.expenses)
            assertEquals(1, updated.entries.count { it.reversed })
        }

    @Test
    fun `отказ в правах и системная статья не допускаются для прочего расхода`() =
        runTest {
            val restricted = ctx.copy(employee = org.athletica.crm.domain.EmployeeStub(id = fixture.employeeId))
            assertIs<Either.Left<AppError>>(attempt(restricted) { payments.pay(null, client, cashPayment(100), today) })
            assertIs<Either.Left<AppError>>(attempt(restricted) { DbCashJournalView().page(CashJournalRequest(today, today)) })
            val system = tx { articles.system("client_refunds") }
            assertIs<Either.Left<AppError>>(attempt { operations.record(CashOperationRequest(cash, system, rub(100), today), CashDirection.EXPENSE) })
        }

    @Test
    fun `касса чужого филиала скрыта во всех проекциях и недоступна для записи и отмены`() =
        runTest {
            val otherBranch = org.athletica.crm.core.entityids.BranchId.new()
            val otherCash = CashAccountId.new()
            val saleId = sale(100)
            val payment =
                tx {
                    contextOf<Transaction>().sql("INSERT INTO branches (id, org_id, name) VALUES (:id, :org, 'North')")
                        .bind("id", otherBranch).bind("org", fixture.orgId).execute()
                    accounts.save(otherCash, "North cash", CashAccountKind.CASH, otherBranch, rub(5000), today)
                    payments.pay(saleId, client, PaymentInput(listOf(CashPaymentInput(otherCash, rub(100)))), today)
                }
            assertEquals("North cash", tx { DbSaleDetailView().byId(saleId) }.operations.single().cash.single().cashAccountName)
            tx {
                contextOf<Transaction>().sql("UPDATE employees SET is_owner = false, all_branches_access = false WHERE id = :id")
                    .bind("id", fixture.employeeId).execute()
                contextOf<Transaction>().sql("INSERT INTO employee_branches (employee_id, branch_id) VALUES (:id, :branch)")
                    .bind("id", fixture.employeeId).bind("branch", fixture.branchId).execute()
            }
            val restricted =
                ctx.copy(
                    employee =
                        org.athletica.crm.domain.EmployeeStub(
                            id = fixture.employeeId,
                            permissionSettings = EmployeePermission(emptyList(), UserPermission.entries.toSet(), emptySet()),
                            availableBranches = org.athletica.crm.domain.employees.EmployeeBranchAccess.Selected(listOf(fixture.branchId)),
                        ),
                )
            assertIs<Either.Left<AppError>>(attempt(restricted) { accounts.activeById(otherCash) })
            assertIs<Either.Left<AppError>>(attempt(restricted) { payments.pay(null, client, PaymentInput(listOf(CashPaymentInput(otherCash, rub(100)))), today) })
            assertIs<Either.Left<AppError>>(attempt(restricted) { payments.reverse(payment, "Error") })
            val balances = attempt(restricted) { DbCashAccountBalancesView().all() }.getOrElse { fail(it.toString()) }
            assertFalse(balances.accounts.any { it.id == otherCash })
            assertEquals(rub(0), balances.total)
            assertEquals(0, attempt(restricted) { DbCashJournalView().page(CashJournalRequest(today, today)) }.getOrElse { fail(it.toString()) }.count)
            assertEquals(rub(0), attempt(restricted) { DbCashFlowReportView().report(CashFlowReportRequest(today, today)) }.getOrElse { fail(it.toString()) }.closing)
            val detail = attempt(restricted) { DbSaleDetailView().byId(saleId) }.getOrElse { fail(it.toString()) }
            assertTrue(detail.operations.isEmpty())
            assertEquals(rub(100), detail.paid)
        }

    @Test
    fun `кассир отменяет свою записанную сегодня оплату даже с прошлой датой операции`() =
        runTest {
            val realToday = kotlin.time.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.UTC).date
            fixture.at(realToday)
            val cashier =
                ctx.copy(
                    employee =
                        org.athletica.crm.domain.EmployeeStub(
                            id = fixture.employeeId,
                            permissionSettings = EmployeePermission(emptyList(), setOf(UserPermission.CAN_ACCEPT_PAYMENTS), emptySet()),
                        ),
                )
            val id = attempt(cashier) { payments.pay(null, client, cashPayment(100), today) }.getOrElse { fail(it.toString()) }
            val options = attempt(cashier) { org.athletica.crm.read.finance.DbClientPaymentOptionsView().byClient(client) }.getOrElse { fail(it.toString()) }
            assertTrue(options.entries.isEmpty())
            assertIs<Either.Left<AppError>>(attempt(cashier) { DbClientLedgerView().byClient(client) })
            assertIs<Either.Right<*>>(attempt(cashier) { payments.reverse(id, "Mistake") })
            assertIs<Either.Left<AppError>>(attempt(cashier) { payments.reverse(id, "Again") })
            assertEquals(rub(0), tx { DbClientLedgerView().byClient(client) }.advance)
            val previousDay = attempt(cashier) { payments.pay(null, client, cashPayment(100), today) }.getOrElse { fail(it.toString()) }
            fixture.at(kotlinx.datetime.LocalDate.fromEpochDays(realToday.toEpochDays() + 1))
            assertIs<Either.Left<AppError>>(attempt(cashier) { payments.reverse(previousDay, "Yesterday") })
            assertIs<Either.Right<*>>(attempt { payments.reverse(previousDay, "Manager correction") })
        }

    @Test
    fun `отмена пополнения не позволяет увести потраченный аванс в минус`() =
        runTest {
            val payment = tx { payments.pay(null, client, cashPayment(1000), today) }
            val id = sale(800)
            tx { payments.pay(id, client, PaymentInput(fromAdvance = rub(800)), today) }
            assertIs<Either.Left<AppError>>(attempt { payments.reverse(payment, "Mistake") })
            assertEquals(rub(200), tx { DbClientLedgerView().byClient(client) }.advance)
        }

    @Test
    fun `возврат с отклонённой выплатой откатывается целиком`() =
        runTest {
            val id = sale(1000)
            tx { payments.pay(id, client, cashPayment(1000), today) }
            assertIs<Either.Left<AppError>>(attempt { actions.returnSale(id, rub(500), "Return", CashAccountId.new()) })
            assertEquals(rub(0), tx { DbSaleDetailView().byId(id) }.returned)
            assertEquals(rub(0), tx { DbClientLedgerView().byClient(client) }.advance)
        }

    @Test
    fun `переплата с частью аванса сохраняет исходную часть оплаты`() =
        runTest {
            tx { payments.pay(null, client, cashPayment(1000), today) }
            val id = sale(4500)
            tx { payments.pay(id, client, PaymentInput(listOf(CashPaymentInput(cash, rub(5000))), rub(1000)), today) }
            val detail = tx { DbSaleDetailView().byId(id) }
            assertEquals(rub(6000), detail.operations.single().amount)
            assertEquals(rub(1000), detail.operations.single().fromAdvance)
            assertEquals(rub(1500), tx { DbClientLedgerView().byClient(client) }.advance)
        }

    @Test
    fun `ДДС группирует статьи и месяцы без двойного счёта итогов и показывает перевод одной кассы`() =
        runTest {
            val group = CashArticleId.new()
            val article = CashArticleId.new()
            tx {
                articles.save(group, "Space", CashDirection.EXPENSE, null, true)
                articles.save(article, "Rent", CashDirection.EXPENSE, group, false)
                operations.record(CashOperationRequest(cash, article, rub(100), today), CashDirection.EXPENSE)
                operations.transfer(TransferCashRequest(cash, bank, rub(200), today))
                articles.archive(article, true)
            }
            val report = tx { DbCashFlowReportView().report(CashFlowReportRequest(LocalDate(2026, 9, 15), LocalDate(2026, 11, 5), listOf(cash))) }
            assertEquals(listOf("2026-09", "2026-10", "2026-11"), report.months)
            assertEquals(rub(100), report.rows.single { it.kind == "group" }.total)
            assertEquals(listOf(rub(0), rub(100), rub(0)), report.rows.single { it.articleId == article }.amounts)
            assertEquals(rub(100), report.expenses)
            assertEquals(rub(-200), report.rows.single { it.kind == "transfer" }.total)
            assertEquals(rub(-300), report.closing)
            val journal = tx { DbCashJournalView().page(CashJournalRequest(today, today, articleIds = listOf(article), limit = 1, offset = 0)) }
            assertEquals(1, journal.count)
            assertEquals(rub(100), journal.expenses)
        }

    @Test
    fun `продажа архивному клиенту отклоняется`() =
        runTest {
            tx { clients.activeById(client).archive() }
            assertIs<Either.Left<AppError>>(attempt { sales.create(SaleId.new(), client, today, listOf(org.athletica.crm.domain.sales.SaleLine("Service", 1, rub(100)))) })
        }

    @Test
    fun `отмена оплаченного абонемента отзывает абонемент и все деньги`() =
        runTest {
            val membership = fixture.issue(client)
            val id = tx { DbClientLedgerView().byClient(client) }.entries.single().saleId!!
            tx { payments.pay(id, client, cashPayment(4000), today) }
            tx { actions.cancel(id, "Wrong issue", fixture.memberships) }
            assertIs<org.athletica.crm.domain.memberships.RevokedMembership>(fixture.reload(membership.id))
            assertEquals(rub(0), tx { DbCashAccountBalancesView().all() }.total)
            assertEquals(rub(0), tx { DbClientLedgerView().byClient(client) }.balance)
        }

    @Test
    fun `отмена продажи абонемента со списанием не меняет продажу и оплату`() =
        runTest {
            val membership = fixture.issue(client)
            val id = tx { DbClientLedgerView().byClient(client) }.entries.single().saleId!!
            tx { payments.pay(id, client, cashPayment(4000), today) }
            tx {
                val tr = contextOf<Transaction>()
                val sessionId = org.athletica.crm.core.entityids.SessionId.new()
                val hallId = org.athletica.crm.core.entityids.HallId.new()
                tr.sql("INSERT INTO halls (id, org_id, branch_id, name) VALUES (:id, :org, :branch, 'Hall')")
                    .bind("id", hallId).bind("org", fixture.orgId).bind("branch", fixture.branchId).execute()
                tr.sql("INSERT INTO sessions (id, org_id, group_id, hall_id, date, start_time, end_time) VALUES (:id, :org, :group, :hall, :date, '10:00', '11:00')")
                    .bind("id", sessionId).bind("org", fixture.orgId).bind("group", fixture.groupId).bind("hall", hallId).bind("date", today).execute()
                tr.sql("INSERT INTO membership_debits (org_id, membership_id, client_id, session_id, kind) VALUES (:org, :membership, :client, :session, 'REGULAR')")
                    .bind("org", fixture.orgId).bind("membership", membership.id).bind("client", client).bind("session", sessionId).execute()
            }
            val error = assertIs<Either.Left<AppError>>(attempt { actions.cancel(id, "Error", fixture.memberships) }).value
            assertEquals("MEMBERSHIP_HAS_DEBITS", error.code)
            assertFalse(tx { DbSaleDetailView().byId(id) }.cancelledAt != null)
            assertEquals(rub(4000), tx { DbCashAccountBalancesView().all() }.total)
        }

    @Test
    fun `приход по статье и неверное направление архив и перевод в ту же кассу`() =
        runTest {
            val income = tx { DbCashArticlesView().all() }.articles.single { it.name == "Прочие доходы" }.id
            tx { operations.record(CashOperationRequest(cash, income, rub(500), today), CashDirection.INCOME) }
            assertEquals(rub(500), tx { DbCashAccountBalancesView().all() }.total)
            assertIs<Either.Left<AppError>>(attempt { operations.record(CashOperationRequest(cash, income, rub(100), today), CashDirection.EXPENSE) })
            assertIs<Either.Left<AppError>>(attempt { operations.transfer(TransferCashRequest(cash, cash, rub(100), today)) })
            tx { articles.archive(income, true) }
            assertIs<Either.Left<AppError>>(attempt { operations.record(CashOperationRequest(cash, income, rub(100), today), CashDirection.INCOME) })
            assertEquals(rub(500), tx { DbCashFlowReportView().report(CashFlowReportRequest(today, today)) }.receipts)
        }
}
