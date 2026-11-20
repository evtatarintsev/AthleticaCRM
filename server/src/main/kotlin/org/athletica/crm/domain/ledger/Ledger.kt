package org.athletica.crm.domain.ledger

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.actingEmployeeId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.LedgerTransactionId
import org.athletica.crm.core.entityids.SaleId
import org.athletica.crm.core.entityids.toCashAccountId
import org.athletica.crm.core.entityids.toCashArticleId
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.entityids.toLedgerAccountId
import org.athletica.crm.core.entityids.toSaleId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.finance.LedgerKind
import org.athletica.crm.core.money.Money
import org.athletica.crm.domain.org.DbOrgCalendar
import org.athletica.crm.domain.org.OrgCalendar
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asMoney
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull

/** Запись главной книги: счета создаются лениво, остатки вычисляются по проводкам. */
class Ledger(private val calendar: OrgCalendar = DbOrgCalendar()) {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun account(owner: LedgerOwner): LedgerAccount {
        val cash = (owner as? LedgerOwner.Cash)?.id
        val client =
            when (owner) {
                is LedgerOwner.Advance -> owner.clientId
                is LedgerOwner.Receivable -> owner.clientId
                else -> null
            }
        val sale = (owner as? LedgerOwner.Receivable)?.saleId
        val article =
            when (owner) {
                is LedgerOwner.Income -> owner.articleId
                is LedgerOwner.Expense -> owner.articleId
                else -> null
            }
        val kind = owner.kind()
        val scope =
            when (owner) {
                is LedgerOwner.Cash -> "EXISTS (SELECT 1 FROM cash_accounts WHERE id = :cash AND org_id = :org)"
                is LedgerOwner.Advance -> "EXISTS (SELECT 1 FROM clients WHERE id = :client AND org_id = :org)"
                is LedgerOwner.Receivable -> "EXISTS (SELECT 1 FROM sales WHERE id = :sale AND client_id = :client AND org_id = :org)"
                is LedgerOwner.Income, is LedgerOwner.Expense -> "EXISTS (SELECT 1 FROM cash_articles WHERE id = :article AND org_id = :org)"
                else -> "true"
            }
        tr.sql(
            """
            INSERT INTO ledger_accounts (org_id, kind, cash_account_id, client_id, sale_id, article_id)
            SELECT :org, :kind, :cash, :client, :sale, :article WHERE $scope
            ON CONFLICT ON CONSTRAINT uq_ledger_account_owner DO NOTHING
            """.trimIndent(),
        ).bind("org", ctx.orgId).bind("kind", kind).bind("cash", cash).bind("client", client)
            .bind("sale", sale).bind("article", article).execute()
        val id =
            tr.sql(
                """
                SELECT id FROM ledger_accounts WHERE org_id = :org AND kind = :kind
                AND cash_account_id IS NOT DISTINCT FROM :cash::uuid AND client_id IS NOT DISTINCT FROM :client::uuid
                AND sale_id IS NOT DISTINCT FROM :sale::uuid AND article_id IS NOT DISTINCT FROM :article::uuid
                """.trimIndent(),
            ).bind("org", ctx.orgId).bind("kind", kind).bind("cash", cash).bind("client", client)
                .bind("sale", sale).bind("article", article).firstOrNull { it.asUuid("id").toLedgerAccountId() }
                ?: raise(ledgerError("LEDGER_ACCOUNT_NOT_FOUND"))
        return LedgerAccount(id, owner)
    }

    /** Единый порядок блокировок аванса и долга предотвращает гонки и взаимные блокировки. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun lock(accounts: List<LedgerAccount>) {
        val ids = accounts.filter { it.owner is LedgerOwner.Advance || it.owner is LedgerOwner.Receivable }.map { it.id }.distinct()
        if (ids.isEmpty()) return
        val locked =
            tr.sql("SELECT id FROM ledger_accounts WHERE org_id = :org AND id = ANY(:ids) ORDER BY id FOR UPDATE")
                .bind("org", ctx.orgId).bind("ids", ids).list { it.asUuid("id") }
        ensure(locked.size == ids.size) { ledgerError("LEDGER_ACCOUNT_NOT_FOUND") }
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun balance(account: LedgerAccount, on: LocalDate): Money =
        tr.sql(
            """
            SELECT COALESCE(SUM(p.amount) FILTER (WHERE t.occurred_on <= :on), 0) AS amount
            FROM ledger_accounts a LEFT JOIN ledger_postings p ON p.account_id = a.id
            LEFT JOIN ledger_transactions t ON t.id = p.transaction_id
            WHERE a.id = :id AND a.org_id = :org GROUP BY a.id
            """.trimIndent(),
        ).bind("id", account.id).bind("org", ctx.orgId).bind("on", on)
            .firstOrNull { it.asMoney("amount", ctx.currency) } ?: raise(ledgerError("LEDGER_ACCOUNT_NOT_FOUND"))

    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun record(
        entry: LedgerEntry,
        occurredOn: LocalDate,
        saleId: SaleId? = null,
        comment: String? = null,
        counterpartyEmployeeId: EmployeeId? = null,
        counterpartyText: String? = null,
        advanceUsed: Money? = null,
    ): LedgerTransactionId {
        if (entry.kind != LedgerKind.ADJUSTMENT) validateAdvance(entry.postings)
        return persist(entry, occurredOn, saleId, comment, null, counterpartyEmployeeId, counterpartyText, advanceUsed)
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun balance(account: LedgerAccount): Money = balance(account, calendar.today())

    /** Сторно датируется сегодня. При отмене продажи аванс проверяется по итоговому набору сторно. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun reverse(id: LedgerTransactionId, reason: String): LedgerTransactionId = reverseAll(listOf(id), reason).single()

    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun reverseAll(ids: List<LedgerTransactionId>, reason: String): List<LedgerTransactionId> {
        ensure(reason.isNotBlank()) { ledgerError("FINANCE_REASON_REQUIRED") }
        val plans = ids.distinct().map { reversalPlan(it) }
        lock(plans.flatMap { it.entry.postings }.map { it.account })
        for (plan in plans) {
            ensure(
                tr.sql("SELECT id FROM ledger_transactions WHERE reverses_id = :id AND org_id = :org")
                    .bind("id", plan.id).bind("org", ctx.orgId).firstOrNull { it.asUuid("id") } == null,
            ) { ledgerError("LEDGER_ALREADY_REVERSED") }
        }
        validateAdvance(plans.flatMap { it.entry.postings })
        return plans.map { persist(it.entry, calendar.today(), it.saleId, reason.trim(), it.id) }
    }

    private data class ReversalPlan(val id: LedgerTransactionId, val entry: LedgerEntry, val saleId: SaleId?)

    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    private suspend fun reversalPlan(id: LedgerTransactionId): ReversalPlan {
        val original =
            tr.sql("SELECT kind, sale_id FROM ledger_transactions WHERE id = :id AND org_id = :org")
                .bind("id", id).bind("org", ctx.orgId)
                .firstOrNull { LedgerKind.valueOf(it.asString("kind").uppercase()) to it.asUuidOrNull("sale_id")?.toSaleId() }
                ?: raise(ledgerError("LEDGER_TRANSACTION_NOT_FOUND"))
        ensure(original.first != LedgerKind.REVERSAL) { ledgerError("LEDGER_REVERSE_REVERSAL") }
        val postings =
            tr.sql(
                """
                SELECT a.*, p.amount, p.article_id AS posting_article_id FROM ledger_postings p
                JOIN ledger_accounts a ON a.id = p.account_id WHERE p.transaction_id = :id AND a.org_id = :org ORDER BY a.id
                """.trimIndent(),
            ).bind("id", id).bind("org", ctx.orgId).list { row ->
                val owner =
                    when (row.asString("kind")) {
                        "cash" -> LedgerOwner.Cash(row.asUuid("cash_account_id").toCashAccountId())
                        "advance" -> LedgerOwner.Advance(row.asUuid("client_id").toClientId())
                        "receivable" -> LedgerOwner.Receivable(row.asUuid("sale_id").toSaleId(), row.asUuid("client_id").toClientId())
                        "income" -> LedgerOwner.Income(row.asUuid("article_id").toCashArticleId())
                        "expense" -> LedgerOwner.Expense(row.asUuid("article_id").toCashArticleId())
                        "revenue" -> LedgerOwner.Revenue
                        "adjustment" -> LedgerOwner.Adjustment
                        else -> LedgerOwner.Opening
                    }
                LedgerPosting(LedgerAccount(row.asUuid("id").toLedgerAccountId(), owner), -row.asMoney("amount", ctx.currency), row.asUuidOrNull("posting_article_id")?.toCashArticleId())
            }
        return ReversalPlan(id, LedgerEntry.of(original.first, postings), original.second)
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    private suspend fun validateAdvance(postings: List<LedgerPosting>) {
        val accounts = postings.map { it.account }
        lock(accounts)
        for ((account, changes) in postings.filter { it.account.owner is LedgerOwner.Advance }.groupBy { it.account }) {
            val after = changes.fold(balance(account)) { sum, posting -> sum + posting.amount }
            ensure(!after.isPositive) { ledgerError("FINANCE_INSUFFICIENT_ADVANCE") }
        }
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    private suspend fun persist(
        entry: LedgerEntry,
        occurredOn: LocalDate,
        saleId: SaleId?,
        comment: String?,
        reversesId: LedgerTransactionId?,
        counterpartyEmployeeId: EmployeeId? = null,
        counterpartyText: String? = null,
        advanceUsed: Money? = null,
    ): LedgerTransactionId {
        ensure(occurredOn <= calendar.today()) { ledgerError("FINANCE_FUTURE_DATE") }
        ensure(counterpartyEmployeeId == null || counterpartyText.isNullOrBlank()) { ledgerError("FINANCE_COUNTERPARTY_INVALID") }
        entry.postings.forEach { ensure(account(it.account.owner) == it.account) { ledgerError("LEDGER_ACCOUNT_NOT_FOUND") } }
        if (counterpartyEmployeeId != null) {
            ensure(tr.sql("SELECT id FROM employees WHERE id = :id AND org_id = :org").bind("id", counterpartyEmployeeId).bind("org", ctx.orgId).firstOrNull { 1 } != null) {
                ledgerError("FINANCE_COUNTERPARTY_INVALID")
            }
        }
        ensure(advanceUsed == null || (advanceUsed.currency == ctx.currency && !advanceUsed.isNegative)) { ledgerError("FINANCE_AMOUNT_INVALID") }
        val id = LedgerTransactionId.new()
        tr.sql(
            """
            INSERT INTO ledger_transactions (id, org_id, kind, occurred_on, sale_id, reverses_id, comment, created_by, counterparty_employee_id, counterparty_text, advance_used)
            VALUES (:id, :org, :kind, :on, :sale, :reverses, :comment, :actor, :employee, :text, :advanceUsed)
            """.trimIndent(),
        ).bind("id", id).bind("org", ctx.orgId).bind("kind", if (reversesId == null) entry.kind.name.lowercase() else "reversal")
            .bind("on", occurredOn).bind("sale", saleId).bind("reverses", reversesId).bind("comment", comment)
            .bind("advanceUsed", advanceUsed ?: Money.zero(ctx.currency)).bind("actor", ctx.actingEmployeeId).bind("employee", counterpartyEmployeeId).bind("text", counterpartyText)
            .onViolation("ledger_transactions_reverses_id_key") { ledgerError("LEDGER_ALREADY_REVERSED") }.execute()
        for (p in entry.postings) {
            tr.sql("INSERT INTO ledger_postings (transaction_id, account_id, amount, article_id) VALUES (:id, :account, :amount, :article)")
                .bind("id", id).bind("account", p.account.id).bind("amount", p.amount).bind("article", p.articleId).execute()
        }
        return id
    }
}

private fun LedgerOwner.kind(): String =
    when (this) {
        is LedgerOwner.Cash -> "cash"
        is LedgerOwner.Advance -> "advance"
        is LedgerOwner.Receivable -> "receivable"
        is LedgerOwner.Income -> "income"
        is LedgerOwner.Expense -> "expense"
        LedgerOwner.Revenue -> "revenue"
        LedgerOwner.Adjustment -> "adjustment"
        LedgerOwner.Opening -> "opening"
    }
