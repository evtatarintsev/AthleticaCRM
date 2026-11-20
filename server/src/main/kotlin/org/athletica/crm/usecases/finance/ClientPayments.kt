package org.athletica.crm.usecases.finance

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.finance.PaymentInput
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.CashAccountId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.LedgerTransactionId
import org.athletica.crm.core.entityids.SaleId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.money.sum
import org.athletica.crm.core.permissions.UserPermission.CAN_ACCEPT_PAYMENTS
import org.athletica.crm.core.permissions.UserPermission.CAN_MANAGE_FINANCE
import org.athletica.crm.core.permissions.requireFinancePermission
import org.athletica.crm.domain.audit.AuditActionType
import org.athletica.crm.domain.audit.AuditEvent
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.PostgresAuditLog
import org.athletica.crm.domain.cashaccounts.CashAccounts
import org.athletica.crm.domain.casharticles.CashArticles
import org.athletica.crm.domain.clients.ActiveClient
import org.athletica.crm.domain.clients.Clients
import org.athletica.crm.domain.clients.DbClients
import org.athletica.crm.domain.clients.activeById
import org.athletica.crm.domain.clients.lockForWrite
import org.athletica.crm.domain.ledger.Ledger
import org.athletica.crm.domain.ledger.LedgerEntries
import org.athletica.crm.domain.ledger.LedgerOwner
import org.athletica.crm.domain.ledger.LedgerPosting
import org.athletica.crm.domain.ledger.ledgerError
import org.athletica.crm.domain.org.DbOrgCalendar
import org.athletica.crm.domain.org.OrgCalendar
import org.athletica.crm.domain.sales.Sales
import org.athletica.crm.i18n.LocalizationKey
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLocalDate
import org.athletica.crm.storage.asUuidOrNull

/** Команды оплаты, аванса и выплат: проверки остатков выполняются под блокировкой счетов. */
class ClientPayments(
    private val ledger: Ledger = Ledger(),
    private val cashAccounts: CashAccounts = CashAccounts(),
    private val articles: CashArticles = CashArticles(),
    private val sales: Sales = Sales(),
    private val clients: Clients = DbClients(),
    private val calendar: OrgCalendar = DbOrgCalendar(),
    private val audit: AuditLog = PostgresAuditLog(),
) {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun pay(
        saleId: SaleId?,
        clientId: ClientId,
        input: PaymentInput,
        occurredOn: LocalDate,
        comment: String? = null,
    ): LedgerTransactionId {
        requireFinancePermission(CAN_ACCEPT_PAYMENTS)
        clients.activeById(clientId).lockForWrite()
        val advance = ledger.account(LedgerOwner.Advance(clientId))
        val receivable = saleId?.let { ledger.account(LedgerOwner.Receivable(it, clientId)) }
        ledger.lock(listOfNotNull(advance, receivable))
        val debt =
            if (saleId != null) {
                val sale = sales.byId(saleId)
                ensure(!sale.cancelled && sale.clientId == clientId) { ledgerError("SALE_CANCELLED") }
                ledger.balance(checkNotNull(receivable))
            } else {
                Money.zero(ctx.currency)
            }
        val fromAdvance = input.fromAdvance ?: Money.zero(ctx.currency)
        ensure(fromAdvance.currency == ctx.currency && !fromAdvance.isNegative && fromAdvance <= debt && fromAdvance <= maxOf(-ledger.balance(advance), Money.zero(ctx.currency))) { ledgerError("FINANCE_INSUFFICIENT_ADVANCE") }
        ensure(input.cash.all { it.amount.currency == ctx.currency && it.amount.isPositive }) { ledgerError("FINANCE_AMOUNT_INVALID") }
        val amount = input.cash.map { it.amount }.sum(ctx.currency) + fromAdvance
        ensure(amount.isPositive) { ledgerError("FINANCE_AMOUNT_INVALID") }
        val article = articles.system("client_receipts")
        val cash =
            input.cash.map {
                cashAccounts.activeById(it.cashAccountId)
                LedgerPosting(ledger.account(LedgerOwner.Cash(it.cashAccountId)), it.amount, article)
            }
        return ledger.record(LedgerEntries.payment(cash, advance, receivable, fromAdvance, debt), occurredOn, saleId, comment, advanceUsed = fromAdvance)
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun payout(
        clientId: ClientId,
        cashAccountId: CashAccountId,
        amount: Money,
        occurredOn: LocalDate,
        comment: String? = null,
        saleId: SaleId? = null,
    ): LedgerTransactionId {
        requireFinancePermission(CAN_MANAGE_FINANCE)
        clients.activeById(clientId).lockForWrite()
        ensure(amount.currency == ctx.currency && amount.isPositive) { ledgerError("FINANCE_AMOUNT_INVALID") }
        cashAccounts.activeById(cashAccountId)
        val advance = ledger.account(LedgerOwner.Advance(clientId))
        ledger.lock(listOf(advance))
        ensure(amount <= -ledger.balance(advance)) { ledgerError("FINANCE_INSUFFICIENT_ADVANCE") }
        return ledger.record(LedgerEntries.payout(advance, ledger.account(LedgerOwner.Cash(cashAccountId)), amount, articles.system("client_refunds")), occurredOn, saleId, comment)
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun adjust(client: ActiveClient, amount: Money, note: String) {
        requireFinancePermission(CAN_MANAGE_FINANCE)
        ensure(amount.currency == ctx.currency && !amount.isZero) { ledgerError("FINANCE_AMOUNT_INVALID") }
        ensure(note.isNotBlank()) { ledgerError("FINANCE_REASON_REQUIRED") }
        client.lockForWrite()
        val advance = ledger.account(LedgerOwner.Advance(client.id))
        ledger.lock(listOf(advance))
        ledger.record(LedgerEntries.adjustment(advance, ledger.account(LedgerOwner.Adjustment), amount), calendar.today(), comment = note.trim())
        audit.log(
            AuditEvent(
                orgId = ctx.orgId, userId = ctx.userId, username = ctx.username, actionType = AuditActionType.BALANCE_ADJUST, ipAddress = ctx.clientIp, entityType = "client", entityId = client.id.value,
                data = """{"amount":${amount.minorUnits},"currency":"${amount.currency.code}","note":${Json.encodeToString(note)}}""",
            ),
        )
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun reverse(id: LedgerTransactionId, reason: String): LedgerTransactionId {
        val original =
            tr.sql("SELECT kind, created_by, (created_at AT TIME ZONE (SELECT timezone FROM organizations WHERE id = :org))::date AS created_on FROM ledger_transactions WHERE id = :id AND org_id = :org")
                .bind("id", id).bind("org", ctx.orgId).firstOrNull {
                    Triple(it.get("kind", String::class.java)!!, it.asUuidOrNull("created_by"), it.asLocalDate("created_on"))
                } ?: raise(ledgerError("LEDGER_TRANSACTION_NOT_FOUND"))
        val allowed = ctx.hasPermission(CAN_MANAGE_FINANCE) || (ctx.hasPermission(CAN_ACCEPT_PAYMENTS) && original.first == "payment" && original.second == ctx.employee.id.value && original.third == calendar.today())
        ensure(allowed) { ledgerError("ACCESS_DENIED") }
        ensure(original.first in setOf("payment", "payout", "adjustment")) { ledgerError("FINANCE_OPERATION_CANNOT_REVERSE") }
        if (original.first == "payment") {
            tr.sql(
                """
                SELECT c.id FROM ledger_transactions t
                JOIN sales s ON s.id = t.sale_id AND s.org_id = t.org_id
                JOIN clients c ON c.id = s.client_id AND c.org_id = s.org_id
                WHERE t.id = :id AND t.org_id = :org FOR UPDATE OF c
                """.trimIndent(),
            ).bind("id", id).bind("org", ctx.orgId).firstOrNull { 1 }
            val hasReturn =
                tr.sql(
                    """
                    SELECT 1 FROM ledger_transactions t WHERE t.id = :id AND t.org_id = :org
                    AND EXISTS (SELECT 1 FROM ledger_transactions returned WHERE returned.sale_id = t.sale_id
                        AND returned.org_id = t.org_id AND returned.kind = 'sale_return'
                        AND NOT EXISTS (SELECT 1 FROM ledger_transactions r WHERE r.reverses_id = returned.id))
                    """.trimIndent(),
                ).bind("id", id).bind("org", ctx.orgId).firstOrNull { 1 } != null
            ensure(!hasReturn) { CommonDomainError("PAYMENT_HAS_RETURN", PaymentHasReturn.localize()) }
        }
        requireTransactionCashAccess(listOf(id), cashAccounts)
        return ledger.reverse(id, reason)
    }
}

private object PaymentHasReturn : LocalizationKey {
    override val ru = "По продаже уже оформлен возврат. Отменить её оплату нельзя."
    override val en = "This sale already has a return. Its payment cannot be reversed."
}
