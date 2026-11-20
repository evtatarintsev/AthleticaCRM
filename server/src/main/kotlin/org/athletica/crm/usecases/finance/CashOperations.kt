package org.athletica.crm.usecases.finance

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import org.athletica.crm.api.schemas.finance.CashOperationRequest
import org.athletica.crm.api.schemas.finance.TransferCashRequest
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.LedgerTransactionId
import org.athletica.crm.core.entityids.toCashAccountId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.finance.CashDirection
import org.athletica.crm.core.finance.LedgerKind
import org.athletica.crm.core.permissions.UserPermission.CAN_MANAGE_FINANCE
import org.athletica.crm.core.permissions.requireFinancePermission
import org.athletica.crm.domain.cashaccounts.CashAccounts
import org.athletica.crm.domain.casharticles.CashArticles
import org.athletica.crm.domain.ledger.Ledger
import org.athletica.crm.domain.ledger.LedgerEntries
import org.athletica.crm.domain.ledger.LedgerOwner
import org.athletica.crm.domain.ledger.ledgerError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

class CashOperations(private val ledger: Ledger = Ledger(), private val accounts: CashAccounts = CashAccounts(), private val articles: CashArticles = CashArticles()) {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun record(request: CashOperationRequest, direction: CashDirection): LedgerTransactionId {
        requireFinancePermission(CAN_MANAGE_FINANCE)
        ensure(request.amount.currency == ctx.currency && request.amount.isPositive) { ledgerError("FINANCE_AMOUNT_INVALID") }
        accounts.activeById(request.cashAccountId)
        val article = articles.byId(request.articleId)
        ensure(!article.archived && !article.isGroup && article.systemCode == null && article.direction == direction) { ledgerError("CASH_ARTICLE_NOT_SELECTABLE") }
        val income = direction == CashDirection.INCOME
        val offset = if (income) LedgerOwner.Income(article.id) else LedgerOwner.Expense(article.id)
        return ledger.record(
            LedgerEntries.cash(if (income) LedgerKind.INCOME else LedgerKind.EXPENSE, ledger.account(LedgerOwner.Cash(request.cashAccountId)), ledger.account(offset), if (income) request.amount else -request.amount, article.id), request.occurredOn,
            comment = request.comment, counterpartyEmployeeId = request.counterpartyEmployeeId, counterpartyText = request.counterpartyText?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun transfer(request: TransferCashRequest): LedgerTransactionId {
        requireFinancePermission(CAN_MANAGE_FINANCE)
        ensure(request.fromCashAccountId != request.toCashAccountId) { ledgerError("CASH_TRANSFER_SAME_ACCOUNT") }
        ensure(request.amount.currency == ctx.currency && request.amount.isPositive) { ledgerError("FINANCE_AMOUNT_INVALID") }
        accounts.activeById(request.fromCashAccountId)
        accounts.activeById(request.toCashAccountId)
        return ledger.record(LedgerEntries.cash(LedgerKind.TRANSFER, ledger.account(LedgerOwner.Cash(request.toCashAccountId)), ledger.account(LedgerOwner.Cash(request.fromCashAccountId)), request.amount), request.occurredOn, comment = request.comment)
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun reverse(id: LedgerTransactionId, reason: String): LedgerTransactionId {
        requireFinancePermission(CAN_MANAGE_FINANCE)
        val kind = tr.sql("SELECT kind FROM ledger_transactions WHERE id = :id AND org_id = :org").bind("id", id).bind("org", ctx.orgId).firstOrNull { it.asString("kind") }
        ensure(kind in setOf("income", "expense", "transfer")) { ledgerError("FINANCE_OPERATION_CANNOT_REVERSE") }
        requireTransactionCashAccess(listOf(id), accounts)
        return ledger.reverse(id, reason)
    }
}

/** Сторно доступно и для архивной кассы, но не для кассы недоступного филиала. */
context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
internal suspend fun requireTransactionCashAccess(ids: List<LedgerTransactionId>, accounts: CashAccounts) {
    if (ids.isEmpty()) return
    val cashIds =
        tr.sql(
            """
            SELECT DISTINCT a.cash_account_id FROM ledger_postings p
            JOIN ledger_accounts a ON a.id = p.account_id AND a.kind = 'cash' AND a.org_id = :org
            JOIN ledger_transactions t ON t.id = p.transaction_id AND t.org_id = :org WHERE t.id = ANY(:ids)
            """.trimIndent(),
        ).bind("org", ctx.orgId).bind("ids", ids).list { it.asUuid("cash_account_id").toCashAccountId() }
    cashIds.forEach { accounts.byId(it) }
}
