package org.athletica.crm.usecases.finance

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import org.athletica.crm.api.schemas.finance.CreateSaleRequest
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.CashAccountId
import org.athletica.crm.core.entityids.SaleId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.permissions.UserPermission.CAN_MANAGE_FINANCE
import org.athletica.crm.core.permissions.requireFinancePermission
import org.athletica.crm.domain.cashaccounts.CashAccounts
import org.athletica.crm.domain.ledger.Ledger
import org.athletica.crm.domain.ledger.LedgerEntries
import org.athletica.crm.domain.ledger.LedgerOwner
import org.athletica.crm.domain.ledger.ledgerError
import org.athletica.crm.domain.memberships.Memberships
import org.athletica.crm.domain.memberships.RevokedMembership
import org.athletica.crm.domain.memberships.revokeManually
import org.athletica.crm.domain.org.DbOrgCalendar
import org.athletica.crm.domain.org.OrgCalendar
import org.athletica.crm.domain.sales.SaleLine
import org.athletica.crm.domain.sales.Sales
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asMoney

/** Продажа, возврат и отмена; каждая команда выполняется внутри одной транзакции БД вызывающего. */
class SaleActions(
    private val memberships: Memberships,
    private val ledger: Ledger = Ledger(),
    private val sales: Sales = Sales(),
    private val payments: ClientPayments = ClientPayments(),
    private val cashAccounts: CashAccounts = CashAccounts(),
    private val calendar: OrgCalendar = DbOrgCalendar(),
) {
    /** Оформляет продажу из [request] и, если в нём есть оплата, сразу принимает её датой продажи. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun create(request: CreateSaleRequest) {
        sales.create(request.id, request.clientId, request.soldOn, request.items.map { SaleLine(it.name, it.quantity, it.unitPrice) })
        request.payment?.let { payments.pay(request.id, request.clientId, it, request.soldOn) }
    }

    /**
     * Оформляет возврат [amount] по продаже [id] с причиной [reason]. Возврат сначала гасит долг
     * по продаже, остаток зачисляется на аванс клиента. Если задана касса [cashAccountId], клиенту
     * выплачивается только зачисленная на аванс часть: погашенный долг деньгами не возвращается.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun returnSale(
        id: SaleId,
        amount: Money,
        reason: String,
        cashAccountId: CashAccountId?,
    ) {
        requireFinancePermission(CAN_MANAGE_FINANCE)
        ensure(reason.isNotBlank()) { ledgerError("FINANCE_REASON_REQUIRED") }
        ensure(amount.currency == ctx.currency && amount.isPositive) { ledgerError("FINANCE_AMOUNT_INVALID") }
        val sale = sales.lockActive(id)
        val returned =
            tr.sql(
                """
                SELECT COALESCE(SUM(p.amount), 0) AS amount FROM ledger_transactions t
                JOIN ledger_postings p ON p.transaction_id = t.id JOIN ledger_accounts a ON a.id = p.account_id AND a.kind = 'revenue'
                WHERE t.org_id = :org AND t.sale_id = :sale AND t.kind = 'sale_return'
                AND NOT EXISTS (SELECT 1 FROM ledger_transactions r WHERE r.reverses_id = t.id)
                """.trimIndent(),
            ).bind("org", ctx.orgId).bind("sale", id).firstOrNull { it.asMoney("amount", ctx.currency) } ?: Money.zero(ctx.currency)
        ensure(amount <= sale.total - returned) { ledgerError("SALE_RETURN_EXCEEDS_TOTAL") }
        val receivable = ledger.account(LedgerOwner.Receivable(id, sale.clientId))
        val advance = ledger.account(LedgerOwner.Advance(sale.clientId))
        val debt = ledger.balance(receivable)
        ledger.record(LedgerEntries.saleReturn(ledger.account(LedgerOwner.Revenue), receivable, advance, amount, debt), calendar.today(), id, reason.trim())
        val credited = amount - minOf(amount, debt)
        if (cashAccountId != null && credited.isPositive) {
            payments.payout(sale.clientId, cashAccountId, credited, calendar.today(), reason.trim(), id)
        }
    }

    /**
     * Отменяет продажу [id] с причиной [reason]: отзывает её абонементы, сторнирует продажу и все
     * неотменённые оплаты, возвраты и выплаты по ней одной операцией. Уже отозванный абонемент
     * пропускается; абонемент, который отозвать нельзя, отклоняет отмену целиком.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun cancel(id: SaleId, reason: String) {
        requireFinancePermission(CAN_MANAGE_FINANCE)
        ensure(reason.isNotBlank()) { ledgerError("FINANCE_REASON_REQUIRED") }
        val sale = sales.lockActive(id)
        sales.membershipIds(sale)
            .map { memberships.byId(it) }
            .filterNot { it is RevokedMembership }
            .forEach { it.revokeManually() }
        val entries = sales.openEntries(sale)
        requireTransactionCashAccess(entries, cashAccounts)
        ledger.reverseAll(entries, reason)
        sales.cancel(sale, reason)
    }
}
