package org.athletica.crm.routes

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import io.ktor.http.HttpHeaders
import kotlinx.datetime.LocalDate
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.athletica.crm.api.schemas.finance.AdjustAdvanceRequest
import org.athletica.crm.api.schemas.finance.ArchiveCashAccountRequest
import org.athletica.crm.api.schemas.finance.ArchiveCashArticleRequest
import org.athletica.crm.api.schemas.finance.CancelSaleRequest
import org.athletica.crm.api.schemas.finance.CashAccountBalancesResponse
import org.athletica.crm.api.schemas.finance.CashArticlesResponse
import org.athletica.crm.api.schemas.finance.CashFlowReportRequest
import org.athletica.crm.api.schemas.finance.CashFlowReportResponse
import org.athletica.crm.api.schemas.finance.CashJournalRequest
import org.athletica.crm.api.schemas.finance.CashJournalResponse
import org.athletica.crm.api.schemas.finance.CashOperationRequest
import org.athletica.crm.api.schemas.finance.ClientLedgerRequest
import org.athletica.crm.api.schemas.finance.ClientLedgerResponse
import org.athletica.crm.api.schemas.finance.CreateSaleRequest
import org.athletica.crm.api.schemas.finance.PaySaleRequest
import org.athletica.crm.api.schemas.finance.PayoutAdvanceRequest
import org.athletica.crm.api.schemas.finance.ReturnSaleRequest
import org.athletica.crm.api.schemas.finance.ReverseTransactionRequest
import org.athletica.crm.api.schemas.finance.SaleDetailResponse
import org.athletica.crm.api.schemas.finance.SaleRequest
import org.athletica.crm.api.schemas.finance.SaveCashAccountRequest
import org.athletica.crm.api.schemas.finance.SaveCashArticleRequest
import org.athletica.crm.api.schemas.finance.TopUpAdvanceRequest
import org.athletica.crm.api.schemas.finance.TransferCashRequest
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.finance.CashDirection
import org.athletica.crm.core.money.formatted
import org.athletica.crm.domain.cashaccounts.CashAccounts
import org.athletica.crm.domain.casharticles.CashArticles
import org.athletica.crm.domain.clients.Clients
import org.athletica.crm.domain.clients.activeById
import org.athletica.crm.domain.org.OrgCalendar
import org.athletica.crm.domain.sales.Sales
import org.athletica.crm.i18n.LocalizationKey
import org.athletica.crm.read.ReadViews
import org.athletica.crm.read.finance.CashFlowReportQuery
import org.athletica.crm.read.finance.CashJournalQuery
import org.athletica.crm.read.finance.SaleDetailQuery
import org.athletica.crm.storage.Database
import org.athletica.crm.usecases.finance.CashOperations
import org.athletica.crm.usecases.finance.ClientPayments
import org.athletica.crm.usecases.finance.SaleActions

/**
 * Финансовые команды и проекции; идентификаторы передаются в теле запроса.
 * Нормализация запросов журнала и ДДС (период, страница, «сегодня» организации из [calendar]) — здесь.
 */
context(db: Database)
fun RouteWithContext.financeRoutes(
    payments: ClientPayments,
    saleActions: SaleActions,
    operations: CashOperations,
    accounts: CashAccounts,
    articles: CashArticles,
    sales: Sales,
    clients: Clients,
    calendar: OrgCalendar,
    views: ReadViews,
) {
    route("/finance") {
        post<Unit, CashAccountBalancesResponse>("/cash-accounts") { db.transaction { views.cashAccountBalances.all() } }
        post<SaveCashAccountRequest, Unit>("/cash-accounts/save") { r -> db.transaction { accounts.save(r.id, r.name, r.kind, r.branchId, r.openingBalance, r.openingOn) } }
        post<ArchiveCashAccountRequest, Unit>("/cash-accounts/archive") { r -> db.transaction { accounts.archive(r.id, r.archived) } }
        post<Unit, CashArticlesResponse>("/cash-articles") { db.transaction { views.cashArticles.all() } }
        post<SaveCashArticleRequest, Unit>("/cash-articles/save") { r -> db.transaction { articles.save(r.id, r.name, r.direction, r.parentId, r.isGroup) } }
        post<ArchiveCashArticleRequest, Unit>("/cash-articles/archive") { r -> db.transaction { articles.archive(r.id, r.archived) } }
        post<ClientLedgerRequest, ClientLedgerResponse>("/client-ledger") { r -> db.transaction { views.clientLedger.byClient(r.clientId) } }
        post<ClientLedgerRequest, ClientLedgerResponse>("/client-payment-options") { r -> db.transaction { views.clientPaymentOptions.byClient(r.clientId) } }
        post<TopUpAdvanceRequest, Unit>("/advance/top-up") { r ->
            db.transaction {
                payments.pay(null, r.clientId, r.payment, r.occurredOn, r.comment)
                Unit
            }
        }
        post<PayoutAdvanceRequest, Unit>("/advance/payout") { r ->
            db.transaction {
                payments.payout(r.clientId, r.cashAccountId, r.amount, r.occurredOn, r.comment)
                Unit
            }
        }
        post<AdjustAdvanceRequest, Unit>("/advance/adjust") { r -> db.transaction { payments.adjust(clients.activeById(r.clientId), r.amount, r.comment) } }
        post<ReverseTransactionRequest, Unit>("/payments/reverse") { r ->
            db.transaction {
                payments.reverse(r.transactionId, r.reason)
                Unit
            }
        }
        post<CashOperationRequest, Unit>("/income") { r ->
            db.transaction {
                operations.record(r, CashDirection.INCOME)
                Unit
            }
        }
        post<CashOperationRequest, Unit>("/expense") { r ->
            db.transaction {
                operations.record(r, CashDirection.EXPENSE)
                Unit
            }
        }
        post<TransferCashRequest, Unit>("/transfer") { r ->
            db.transaction {
                operations.transfer(r)
                Unit
            }
        }
        post<ReverseTransactionRequest, Unit>("/operations/reverse") { r ->
            db.transaction {
                operations.reverse(r.transactionId, r.reason)
                Unit
            }
        }
        post<CashJournalRequest, CashJournalResponse>("/journal") { r -> db.transaction { views.cashJournal.page(r.toQuery(calendar.today(), r.limit.coerceIn(1, 100), r.offset.coerceAtLeast(0))) } }
        post<CashFlowReportRequest, CashFlowReportResponse>("/cash-flow") { r -> db.transaction { views.cashFlowReport.report(r.toQuery()) } }
        post<CashJournalRequest, ByteArray>("/journal/export") { r, call ->
            val result = db.transaction { views.cashJournal.page(r.toQuery(calendar.today(), Int.MAX_VALUE, 0)) }
            call.response.headers.append(HttpHeaders.ContentType, "text/csv; charset=UTF-8")
            call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=\"cash-journal.csv\"")
            financeCsv(
                listOf("Date", "Kind", "Cash account", "Article", "Amount", "Client", "Sale", "Counterparty", "Comment", "Author", "Reversed", "Reversal reason", "Reversed by"),
                result.entries.map {
                    listOf(it.occurredOn.toString(), it.kind.name, listOfNotNull(it.cashAccountName, it.counterCashAccountName).joinToString(" / "), it.articleName.orEmpty(), it.amount.formatted, it.clientName.orEmpty(), it.saleId?.toString().orEmpty(), it.counterparty.orEmpty(), it.comment.orEmpty(), it.performedBy?.name.orEmpty(), it.reversed.toString(), it.reversalReason.orEmpty(), it.reversedBy?.name.orEmpty())
                },
            )
        }
        post<CashFlowReportRequest, ByteArray>("/cash-flow/export") { r, call ->
            val result = db.transaction { views.cashFlowReport.report(r.toQuery()) }
            call.response.headers.append(HttpHeaders.ContentType, "text/csv; charset=UTF-8")
            call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=\"cash-flow.csv\"")
            financeCsv(
                listOf("Article", "Group") + result.months + "Total",
                result.rows.map { listOf(it.name, it.parentName.orEmpty()) + it.amounts.map { amount -> amount.formatted } + it.total.formatted } +
                    listOf(listOf("Opening", "") + result.months.map { "" } + result.opening.formatted, listOf("Closing", "") + result.months.map { "" } + result.closing.formatted),
            )
        }
    }
    route("/sales") {
        post<CreateSaleRequest, Unit>("/create") { r -> db.transaction { saleActions.create(r) } }
        post<SaleRequest, SaleDetailResponse>("/detail") { r -> db.transaction { views.saleDetail.byId(SaleDetailQuery(r.saleId, calendar.today())) } }
        post<PaySaleRequest, Unit>("/pay") { r ->
            db.transaction {
                payments.pay(r.saleId, sales.byId(r.saleId).clientId, r.payment, r.occurredOn, r.comment)
                Unit
            }
        }
        post<ReturnSaleRequest, Unit>("/return") { r -> db.transaction { saleActions.returnSale(r.saleId, r.amount, r.reason, r.cashAccountId) } }
        post<CancelSaleRequest, Unit>("/cancel") { r -> db.transaction { saleActions.cancel(r.saleId, r.reason) } }
    }
}

/** Нормализованный запрос журнала со страницей [limit]/[offset] и сегодняшней датой [today]. */
context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
private fun CashJournalRequest.toQuery(today: LocalDate, limit: Int, offset: Int): CashJournalQuery {
    ensurePeriod(from, to)
    return CashJournalQuery(from, to, cashAccountIds, articleIds, kinds, clientId, employeeId, includeReversed, limit, offset, today)
}

/** Нормализованный запрос ДДС. */
context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
private fun CashFlowReportRequest.toQuery(): CashFlowReportQuery {
    ensurePeriod(from, to)
    return CashFlowReportQuery(from, to, cashAccountIds)
}

/** Проверяет, что период [from]..[to] не перевёрнут. */
context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
private fun ensurePeriod(from: LocalDate, to: LocalDate) {
    ensure(from <= to) { CommonDomainError("FINANCE_PERIOD_INVALID", FinancePeriodInvalid.localize()) }
}

private object FinancePeriodInvalid : LocalizationKey {
    override val ru = "Начало периода позже его конца"
    override val en = "The period start is after its end"
}

/**
 * CSV-файл с заголовками [headers] и строками [rows] в UTF-8 с BOM. Значения, которые табличный
 * редактор принял бы за формулу, экранируются апострофом.
 */
private fun financeCsv(headers: List<String>, rows: List<List<String>>): ByteArray {
    val text = StringBuilder()
    CSVPrinter(text, CSVFormat.DEFAULT.builder().setHeader(*headers.toTypedArray()).get()).use { csv ->
        rows.forEach { row -> csv.printRecord(row.map { if (it.trimStart().firstOrNull() in listOf('=', '+', '-', '@') || it.firstOrNull() in listOf('\t', '\r', '\n')) "'$it" else it }) }
    }
    return ("\uFEFF" + text).toByteArray(Charsets.UTF_8)
}
