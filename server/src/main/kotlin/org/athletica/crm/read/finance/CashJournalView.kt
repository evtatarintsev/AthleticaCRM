package org.athletica.crm.read.finance

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.finance.CashJournalRequest
import org.athletica.crm.api.schemas.finance.CashJournalResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.permissions.UserPermission.CAN_VIEW_FINANCE
import org.athletica.crm.core.permissions.requireFinancePermission
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString

/** Журнал показывает исходные операции, а сторно — признаком отмены; итоги исключают отменённые. */
interface CashJournalView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun page(query: CashJournalRequest): CashJournalResponse
}

class DbCashJournalView : CashJournalView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun page(query: CashJournalRequest): CashJournalResponse {
        requireFinancePermission(CAN_VIEW_FINANCE)
        ensure(query.from <= query.to) { CommonDomainError("FINANCE_PERIOD_INVALID", "Invalid journal period") }
        return checkNotNull(
            tr.sql(SQL).bind("org", ctx.orgId).bind("employee", ctx.employee.id).bind("currency", ctx.currency.code)
                .bind("from", query.from).bind("to", query.to).bind("allCash", query.cashAccountIds.isEmpty()).bind("cashIds", query.cashAccountIds)
                .bind("allArticles", query.articleIds.isEmpty()).bind("articles", query.articleIds).bind("allKinds", query.kinds.isEmpty())
                .bind("kinds", query.kinds.map { it.name.lowercase() }.toTypedArray()).bind("client", query.clientId).bind("author", query.employeeId)
                .bind("canManage", ctx.hasPermission(org.athletica.crm.core.permissions.UserPermission.CAN_MANAGE_FINANCE))
                .bind("canAccept", ctx.hasPermission(org.athletica.crm.core.permissions.UserPermission.CAN_ACCEPT_PAYMENTS))
                .bind("includeReversed", query.includeReversed).bind("limit", query.limit).bind("offset", query.offset)
                .firstOrNull { Json.decodeFromString<CashJournalResponse>(it.asString("response")) },
        )
    }

    private companion object {
        val SQL =
            """
            WITH visible_cash AS (SELECT c.* FROM cash_accounts c WHERE c.org_id = :org AND $CASH_ACCESS_SQL), raw AS (
                SELECT t.id, t.kind, t.occurred_on, t.created_at, t.sale_id, t.comment, t.created_by,
                    c.id AS cash_id, c.name AS cash_name, p.id AS posting_id, p.amount, p.article_id, article.name AS article_name,
                    COALESCE(s.client_id, (SELECT a2.client_id FROM ledger_postings p2 JOIN ledger_accounts a2 ON a2.id = p2.account_id
                        WHERE p2.transaction_id = t.id AND a2.org_id = :org AND a2.client_id IS NOT NULL LIMIT 1)) AS client_id,
                    COALESCE(t.counterparty_text, counterparty.name) AS counterparty,
                    e.name AS actor_name, r.id AS reversal_id, r.comment AS reversal_reason, r.created_by AS reversal_actor, re.name AS reversal_actor_name,
                    (SELECT c2.id FROM ledger_postings p2 JOIN ledger_accounts a2 ON a2.id = p2.account_id AND a2.kind = 'cash'
                        JOIN visible_cash c2 ON c2.id = a2.cash_account_id WHERE p2.transaction_id = t.id AND c2.id <> c.id LIMIT 1) AS counter_cash_id,
                    (SELECT c2.name FROM ledger_postings p2 JOIN ledger_accounts a2 ON a2.id = p2.account_id AND a2.kind = 'cash'
                        JOIN visible_cash c2 ON c2.id = a2.cash_account_id WHERE p2.transaction_id = t.id AND c2.id <> c.id LIMIT 1) AS counter_cash_name,
                    $TRANSACTION_CASH_ACCESS_SQL AS cash_accessible,
                    $SALE_HAS_RETURN_SQL AS sale_has_return,
                    row_number() OVER (PARTITION BY t.id ORDER BY p.amount DESC, p.id) AS cash_rank
                FROM visible_cash c JOIN ledger_accounts a ON a.cash_account_id = c.id AND a.org_id = :org AND a.kind = 'cash'
                JOIN ledger_postings p ON p.account_id = a.id JOIN ledger_transactions t ON t.id = p.transaction_id AND t.org_id = :org
                LEFT JOIN cash_articles article ON article.id = p.article_id AND article.org_id = :org
                LEFT JOIN sales s ON s.id = t.sale_id AND s.org_id = :org
                LEFT JOIN employees e ON e.id = t.created_by AND e.org_id = :org
                LEFT JOIN employees counterparty ON counterparty.id = t.counterparty_employee_id AND counterparty.org_id = :org
                LEFT JOIN ledger_transactions r ON r.reverses_id = t.id AND r.org_id = :org
                LEFT JOIN employees re ON re.id = r.created_by AND re.org_id = :org
                WHERE t.kind <> 'reversal' AND t.occurred_on BETWEEN :from AND :to
                AND (:allCash OR c.id = ANY(:cashIds)) AND (:allArticles OR p.article_id = ANY(:articles))
                AND (:allKinds OR t.kind = ANY(:kinds)) AND (:author::uuid IS NULL OR t.created_by = :author)
                AND (:includeReversed OR r.id IS NULL)
            ), filtered AS (
                SELECT raw.*, client.name AS client_name FROM raw LEFT JOIN clients client ON client.id = raw.client_id AND client.org_id = :org
                WHERE (kind <> 'transfer' OR cash_rank = 1) AND (:client::uuid IS NULL OR raw.client_id = :client)
            ), page AS (SELECT * FROM filtered ORDER BY occurred_on DESC, created_at DESC, id DESC, posting_id LIMIT :limit OFFSET :offset)
            SELECT jsonb_build_object(
                'count', (SELECT COUNT(*) FROM filtered),
                'receipts', ${moneyJsonSql("COALESCE((SELECT SUM(amount) FROM filtered WHERE amount > 0 AND reversal_id IS NULL AND kind NOT IN ('transfer', 'opening')), 0)")},
                'expenses', ${moneyJsonSql("COALESCE((SELECT -SUM(amount) FROM filtered WHERE amount < 0 AND reversal_id IS NULL AND kind NOT IN ('transfer', 'opening')), 0)")},
                'entries', COALESCE((SELECT jsonb_agg(jsonb_build_object(
                    'id', id, 'kind', upper(kind), 'occurredOn', occurred_on, 'cashAccountId', cash_id, 'cashAccountName', cash_name,
                    'counterCashAccountId', counter_cash_id, 'counterCashAccountName', counter_cash_name,
                    'articleId', article_id, 'articleName', article_name, 'amount', ${moneyJsonSql("amount")},
                    'clientId', client_id, 'clientName', client_name, 'saleId', sale_id, 'counterparty', counterparty, 'comment', comment,
                    'performedBy', CASE WHEN actor_name IS NULL THEN NULL ELSE jsonb_build_object('id', created_by, 'name', actor_name) END,
                    'reversed', reversal_id IS NOT NULL, 'reversalReason', reversal_reason,
                    'canReverse', cash_accessible AND (kind <> 'payment' OR NOT sale_has_return) AND reversal_id IS NULL AND ((:canManage AND kind IN ('income', 'expense', 'transfer', 'payment', 'payout')) OR
                        (:canAccept AND kind = 'payment' AND created_by = :employee AND
                        (created_at AT TIME ZONE (SELECT timezone FROM organizations WHERE id = :org))::date =
                        (CURRENT_TIMESTAMP AT TIME ZONE (SELECT timezone FROM organizations WHERE id = :org))::date)),
                    'reversedBy', CASE WHEN reversal_actor_name IS NULL THEN NULL ELSE jsonb_build_object('id', reversal_actor, 'name', reversal_actor_name) END
                ) ORDER BY occurred_on DESC, created_at DESC, id DESC, posting_id) FROM page), '[]')
            )::text AS response
            """.trimIndent()
    }
}
