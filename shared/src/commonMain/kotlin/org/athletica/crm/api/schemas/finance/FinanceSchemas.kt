package org.athletica.crm.api.schemas.finance

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.athletica.crm.api.schemas.common.PerformedBy
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.CashAccountId
import org.athletica.crm.core.entityids.CashArticleId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.LedgerTransactionId
import org.athletica.crm.core.entityids.SaleId
import org.athletica.crm.core.entityids.SaleItemId
import org.athletica.crm.core.finance.CashAccountKind
import org.athletica.crm.core.finance.CashDirection
import org.athletica.crm.core.finance.LedgerKind
import org.athletica.crm.core.finance.SaleItemKind
import org.athletica.crm.core.finance.SalePaymentState
import org.athletica.crm.core.money.Money
import kotlin.time.Instant

/** Касса с текущим остатком; архивные кассы сохраняют историю. */
@Serializable
data class CashAccountSchema(
    val id: CashAccountId,
    val name: String,
    val kind: CashAccountKind,
    val branchId: BranchId?,
    val archived: Boolean,
    val balance: Money,
)

@Serializable
data class CashAccountBalancesResponse(val accounts: List<CashAccountSchema>, val total: Money)

@Serializable
data class SaveCashAccountRequest(
    val id: CashAccountId,
    val name: String,
    val kind: CashAccountKind,
    val branchId: BranchId? = null,
    val openingBalance: Money? = null,
    val openingOn: LocalDate? = null,
)

@Serializable
data class ArchiveCashAccountRequest(val id: CashAccountId, val archived: Boolean)

@Serializable
data class CashArticleSchema(
    val id: CashArticleId,
    val name: String,
    val direction: CashDirection,
    val parentId: CashArticleId?,
    val isGroup: Boolean,
    val systemCode: String?,
    val archived: Boolean,
)

@Serializable
data class CashArticlesResponse(val articles: List<CashArticleSchema>)

@Serializable
data class SaveCashArticleRequest(
    val id: CashArticleId,
    val name: String,
    val direction: CashDirection,
    val parentId: CashArticleId? = null,
    val isGroup: Boolean = false,
)

@Serializable
data class ArchiveCashArticleRequest(val id: CashArticleId, val archived: Boolean)

@Serializable
data class CashPaymentInput(val cashAccountId: CashAccountId, val amount: Money)

/** Вся оплата — одна транзакция; кассовая переплата зачисляется на аванс. */
@Serializable
data class PaymentInput(val cash: List<CashPaymentInput> = emptyList(), val fromAdvance: Money? = null)

@Serializable
data class SaleItemInput(val name: String, val quantity: Int, val unitPrice: Money)

@Serializable
data class CreateSaleRequest(
    val id: SaleId,
    val clientId: ClientId,
    val soldOn: LocalDate,
    val items: List<SaleItemInput>,
    val payment: PaymentInput? = null,
)

@Serializable
data class SaleRequest(val saleId: SaleId)

@Serializable
data class PaySaleRequest(
    val saleId: SaleId,
    val occurredOn: LocalDate,
    val payment: PaymentInput,
    val comment: String? = null,
)

@Serializable
data class TopUpAdvanceRequest(
    val clientId: ClientId,
    val occurredOn: LocalDate,
    val payment: PaymentInput,
    val comment: String? = null,
)

@Serializable
data class PayoutAdvanceRequest(
    val clientId: ClientId,
    val cashAccountId: CashAccountId,
    val amount: Money,
    val occurredOn: LocalDate,
    val comment: String? = null,
)

/** Сумма со знаком: плюс зачисляет аванс, минус списывает; комментарий обязателен. */
@Serializable
data class AdjustAdvanceRequest(val clientId: ClientId, val amount: Money, val comment: String)

@Serializable
data class ReverseTransactionRequest(val transactionId: LedgerTransactionId, val reason: String)

@Serializable
data class ReturnSaleRequest(
    val saleId: SaleId,
    val amount: Money,
    val reason: String,
    val cashAccountId: CashAccountId? = null,
)

@Serializable
data class CancelSaleRequest(val saleId: SaleId, val reason: String)

@Serializable
data class ClientLedgerRequest(val clientId: ClientId)

@Serializable
data class ClientLedgerEntry(
    val id: LedgerTransactionId,
    val kind: LedgerKind,
    val occurredOn: LocalDate,
    val createdAt: Instant,
    val amount: Money,
    val balanceAfter: Money,
    val fromAdvance: Money,
    val performedBy: PerformedBy?,
    val comment: String?,
    val saleId: SaleId?,
    val reversed: Boolean,
)

@Serializable
data class ClientDebtSchema(val saleId: SaleId, val soldOn: LocalDate, val debt: Money)

@Serializable
data class ClientLedgerResponse(
    val balance: Money,
    val advance: Money,
    val debt: Money,
    val entries: List<ClientLedgerEntry>,
    val salesWithDebt: List<ClientDebtSchema>,
)

@Serializable
data class SaleItemSchema(
    val id: SaleItemId,
    val kind: SaleItemKind,
    val name: String,
    val quantity: Int,
    val unitPrice: Money,
    val amount: Money,
)

@Serializable
data class SaleCashPartSchema(val cashAccountId: CashAccountId, val cashAccountName: String, val amount: Money)

@Serializable
data class SaleOperationSchema(
    val id: LedgerTransactionId,
    val kind: LedgerKind,
    val occurredOn: LocalDate,
    val amount: Money,
    val cash: List<SaleCashPartSchema>,
    val fromAdvance: Money,
    val performedBy: PerformedBy?,
    val comment: String?,
    val reversed: Boolean,
    val canReverse: Boolean,
)

@Serializable
data class SaleDetailResponse(
    val id: SaleId,
    val clientId: ClientId,
    val soldOn: LocalDate,
    val performedBy: PerformedBy?,
    val items: List<SaleItemSchema>,
    val total: Money,
    val paid: Money,
    val returned: Money,
    val debt: Money,
    val paymentState: SalePaymentState,
    val cancelledAt: Instant?,
    val cancelReason: String?,
    val operations: List<SaleOperationSchema>,
)

@Serializable
data class CashOperationRequest(
    val cashAccountId: CashAccountId,
    val articleId: CashArticleId,
    val amount: Money,
    val occurredOn: LocalDate,
    val counterpartyEmployeeId: EmployeeId? = null,
    val counterpartyText: String? = null,
    val comment: String? = null,
)

@Serializable
data class TransferCashRequest(
    val fromCashAccountId: CashAccountId,
    val toCashAccountId: CashAccountId,
    val amount: Money,
    val occurredOn: LocalDate,
    val comment: String? = null,
)

@Serializable
data class CashJournalRequest(
    val from: LocalDate,
    val to: LocalDate,
    val cashAccountIds: List<CashAccountId> = emptyList(),
    val articleIds: List<CashArticleId> = emptyList(),
    val kinds: List<LedgerKind> = emptyList(),
    val clientId: ClientId? = null,
    val employeeId: EmployeeId? = null,
    val includeReversed: Boolean = true,
    val limit: Int = 50,
    val offset: Int = 0,
)

@Serializable
data class CashJournalEntry(
    val id: LedgerTransactionId,
    val kind: LedgerKind,
    val occurredOn: LocalDate,
    val cashAccountId: CashAccountId,
    val cashAccountName: String,
    val articleId: CashArticleId?,
    val articleName: String?,
    val amount: Money,
    val clientId: ClientId?,
    val clientName: String?,
    val saleId: SaleId?,
    val counterparty: String?,
    val comment: String?,
    val performedBy: PerformedBy?,
    val reversed: Boolean,
    val reversalReason: String?,
    val reversedBy: PerformedBy?,
    val counterCashAccountId: CashAccountId? = null,
    val counterCashAccountName: String? = null,
    val canReverse: Boolean = false,
)

@Serializable
data class CashJournalResponse(
    val entries: List<CashJournalEntry>,
    val count: Int,
    val receipts: Money,
    val expenses: Money,
)

@Serializable
data class CashFlowReportRequest(val from: LocalDate, val to: LocalDate, val cashAccountIds: List<CashAccountId> = emptyList())

@Serializable
data class CashFlowRow(
    val articleId: CashArticleId?,
    val name: String,
    val parentId: CashArticleId?,
    val parentName: String?,
    val direction: CashDirection?,
    val kind: String,
    val amounts: List<Money>,
    val total: Money,
)

@Serializable
data class CashFlowReportResponse(
    val months: List<String>,
    val rows: List<CashFlowRow>,
    val opening: Money,
    val closing: Money,
    val receipts: Money,
    val expenses: Money,
)
