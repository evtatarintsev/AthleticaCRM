package org.athletica.crm.routes

import io.ktor.http.HttpHeaders
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
import org.athletica.crm.core.finance.CashDirection
import org.athletica.crm.core.money.formatted
import org.athletica.crm.domain.cashaccounts.CashAccounts
import org.athletica.crm.domain.casharticles.CashArticles
import org.athletica.crm.domain.clients.Clients
import org.athletica.crm.domain.clients.activeById
import org.athletica.crm.domain.memberships.Memberships
import org.athletica.crm.domain.sales.Sales
import org.athletica.crm.read.finance.DbCashAccountBalancesView
import org.athletica.crm.read.finance.DbCashArticlesView
import org.athletica.crm.read.finance.DbCashFlowReportView
import org.athletica.crm.read.finance.DbCashJournalView
import org.athletica.crm.read.finance.DbClientLedgerView
import org.athletica.crm.read.finance.DbClientPaymentOptionsView
import org.athletica.crm.read.finance.DbSaleDetailView
import org.athletica.crm.storage.Database
import org.athletica.crm.usecases.finance.CashOperations
import org.athletica.crm.usecases.finance.ClientPayments
import org.athletica.crm.usecases.finance.SaleActions

/** Финансовые команды и проекции; идентификаторы передаются в теле запроса. */
context(db: Database)
fun RouteWithContext.financeRoutes(payments: ClientPayments, clients: Clients, memberships: Memberships) {
    val accounts = CashAccounts()
    val articles = CashArticles()
    val sales = Sales()
    val saleActions = SaleActions(payments = payments)
    val operations = CashOperations()
    val cashBalances = DbCashAccountBalancesView()
    val cashArticles = DbCashArticlesView()
    val clientLedger = DbClientLedgerView()
    val clientPaymentOptions = DbClientPaymentOptionsView()
    val saleDetail = DbSaleDetailView()
    val journal = DbCashJournalView()
    val report = DbCashFlowReportView()
    route("/finance") {
        post<Unit, CashAccountBalancesResponse>("/cash-accounts") { db.transaction { cashBalances.all() } }
        post<SaveCashAccountRequest, Unit>("/cash-accounts/save") { r -> db.transaction { accounts.save(r.id, r.name, r.kind, r.branchId, r.openingBalance, r.openingOn) } }
        post<ArchiveCashAccountRequest, Unit>("/cash-accounts/archive") { r -> db.transaction { accounts.archive(r.id, r.archived) } }
        post<Unit, CashArticlesResponse>("/cash-articles") { db.transaction { cashArticles.all() } }
        post<SaveCashArticleRequest, Unit>("/cash-articles/save") { r -> db.transaction { articles.save(r.id, r.name, r.direction, r.parentId, r.isGroup) } }
        post<ArchiveCashArticleRequest, Unit>("/cash-articles/archive") { r -> db.transaction { articles.archive(r.id, r.archived) } }
        post<ClientLedgerRequest, ClientLedgerResponse>("/client-ledger") { r -> db.transaction { clientLedger.byClient(r.clientId) } }
        post<ClientLedgerRequest, ClientLedgerResponse>("/client-payment-options") { r -> db.transaction { clientPaymentOptions.byClient(r.clientId) } }
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
        post<CashJournalRequest, CashJournalResponse>("/journal") { r -> db.transaction { journal.page(r.copy(limit = r.limit.coerceIn(1, 100), offset = r.offset.coerceAtLeast(0))) } }
        post<CashFlowReportRequest, CashFlowReportResponse>("/cash-flow") { r -> db.transaction { report.report(r) } }
        post<CashJournalRequest, ByteArray>("/journal/export") { r, call ->
            val result = db.transaction { journal.page(r.copy(limit = Int.MAX_VALUE, offset = 0)) }
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
            val result = db.transaction { report.report(r) }
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
        post<SaleRequest, SaleDetailResponse>("/detail") { r -> db.transaction { saleDetail.byId(r.saleId) } }
        post<PaySaleRequest, Unit>("/pay") { r ->
            db.transaction {
                payments.pay(r.saleId, sales.byId(r.saleId).clientId, r.payment, r.occurredOn, r.comment)
                Unit
            }
        }
        post<ReturnSaleRequest, Unit>("/return") { r -> db.transaction { saleActions.returnSale(r.saleId, r.amount, r.reason, r.cashAccountId) } }
        post<CancelSaleRequest, Unit>("/cancel") { r -> db.transaction { saleActions.cancel(r.saleId, r.reason, memberships) } }
    }
}

private fun financeCsv(headers: List<String>, rows: List<List<String>>): ByteArray {
    val text = StringBuilder()
    CSVPrinter(text, CSVFormat.DEFAULT.builder().setHeader(*headers.toTypedArray()).get()).use { csv ->
        rows.forEach { row -> csv.printRecord(row.map { if (it.trimStart().firstOrNull() in listOf('=', '+', '-', '@') || it.firstOrNull() in listOf('\t', '\r', '\n')) "'$it" else it }) }
    }
    return ("\uFEFF" + text).toByteArray(Charsets.UTF_8)
}
