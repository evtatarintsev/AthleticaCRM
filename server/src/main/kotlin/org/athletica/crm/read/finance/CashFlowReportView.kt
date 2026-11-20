package org.athletica.crm.read.finance

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.finance.CashFlowReportRequest
import org.athletica.crm.api.schemas.finance.CashFlowReportResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.permissions.UserPermission.CAN_VIEW_FINANCE
import org.athletica.crm.core.permissions.requireFinancePermission
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString

/** ДДС по датам проводок: сторно не переписывает историю предыдущих месяцев. */
interface CashFlowReportView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun report(query: CashFlowReportRequest): CashFlowReportResponse
}

class DbCashFlowReportView : CashFlowReportView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun report(query: CashFlowReportRequest): CashFlowReportResponse {
        requireFinancePermission(CAN_VIEW_FINANCE)
        ensure(query.from <= query.to) { CommonDomainError("FINANCE_PERIOD_INVALID", "Invalid report period") }
        return checkNotNull(
            tr.sql(SQL).bind("org", ctx.orgId).bind("employee", ctx.employee.id).bind("currency", ctx.currency.code)
                .bind("from", query.from).bind("to", query.to).bind("allCash", query.cashAccountIds.isEmpty()).bind("cashIds", query.cashAccountIds)
                .firstOrNull { Json.decodeFromString<CashFlowReportResponse>(it.asString("response")) },
        )
    }

    private companion object {
        val SQL =
            """
            WITH cash AS (
                SELECT c.id FROM cash_accounts c WHERE c.org_id = :org
                AND (:allCash OR c.id = ANY(:cashIds)) AND $CASH_ACCESS_SQL
            ), postings AS (
                SELECT p.amount, p.article_id, t.occurred_on, COALESCE(original.kind, t.kind) AS kind
                FROM cash c JOIN ledger_accounts a ON a.cash_account_id = c.id AND a.org_id = :org AND a.kind = 'cash'
                JOIN ledger_postings p ON p.account_id = a.id JOIN ledger_transactions t ON t.id = p.transaction_id AND t.org_id = :org
                LEFT JOIN ledger_transactions original ON original.id = t.reverses_id AND original.org_id = t.org_id
                WHERE t.occurred_on <= :to
            ), months AS (
                SELECT generate_series(date_trunc('month', :from::date), date_trunc('month', :to::date), interval '1 month')::date AS month
            ), movement AS (
                SELECT date_trunc('month', p.occurred_on)::date AS month, p.article_id,
                    CASE WHEN p.kind = 'opening' THEN 'opening' WHEN p.kind = 'transfer' THEN 'transfer' ELSE 'article' END AS row_kind,
                    SUM(p.amount) AS amount
                FROM postings p WHERE p.occurred_on >= :from GROUP BY 1, 2, 3
            ), grouped_movement AS (
                SELECT * FROM movement
                UNION ALL
                SELECT v.month, a.parent_id AS article_id, 'group' AS row_kind, SUM(v.amount) AS amount
                FROM movement v JOIN cash_articles a ON a.id = v.article_id AND a.org_id = :org
                WHERE a.parent_id IS NOT NULL GROUP BY v.month, a.parent_id
            ), row_keys AS (SELECT DISTINCT article_id, row_kind FROM grouped_movement), rows AS (
                SELECT k.article_id, k.row_kind, a.name, a.direction, a.parent_id, parent.name AS parent_name,
                    jsonb_agg(${moneyJsonSql("CASE WHEN a.direction = 'expense' THEN -COALESCE(v.amount, 0) ELSE COALESCE(v.amount, 0) END")} ORDER BY m.month) AS amounts,
                    SUM(CASE WHEN a.direction = 'expense' THEN -COALESCE(v.amount, 0) ELSE COALESCE(v.amount, 0) END) AS total
                FROM row_keys k CROSS JOIN months m
                LEFT JOIN grouped_movement v ON v.month = m.month AND v.article_id IS NOT DISTINCT FROM k.article_id AND v.row_kind = k.row_kind
                LEFT JOIN cash_articles a ON a.id = k.article_id AND a.org_id = :org
                LEFT JOIN cash_articles parent ON parent.id = a.parent_id AND parent.org_id = :org
                GROUP BY k.article_id, k.row_kind, a.name, a.direction, a.parent_id, parent.name HAVING bool_or(COALESCE(v.amount, 0) <> 0)
            ), totals AS (
                SELECT COALESCE(SUM(amount) FILTER (WHERE occurred_on < :from), 0) AS opening,
                    COALESCE(SUM(amount), 0) AS closing FROM postings
            )
            SELECT jsonb_build_object(
                'months', (SELECT jsonb_agg(to_char(month, 'YYYY-MM') ORDER BY month) FROM months),
                'rows', COALESCE((SELECT jsonb_agg(jsonb_build_object(
                    'articleId', article_id, 'name', COALESCE(name, row_kind), 'parentId', parent_id, 'parentName', parent_name,
                    'direction', upper(direction), 'kind', row_kind, 'amounts', amounts, 'total', ${moneyJsonSql("total")}
                ) ORDER BY direction NULLS FIRST, COALESCE(parent_name, name, row_kind), (row_kind = 'group') DESC, name) FROM rows), '[]'),
                'opening', ${moneyJsonSql("opening")}, 'closing', ${moneyJsonSql("closing")},
                'receipts', ${moneyJsonSql("COALESCE((SELECT SUM(total) FROM rows WHERE direction = 'income' AND row_kind <> 'group'), 0)")},
                'expenses', ${moneyJsonSql("COALESCE((SELECT SUM(total) FROM rows WHERE direction = 'expense' AND row_kind <> 'group'), 0)")}
            )::text AS response FROM totals
            """.trimIndent()
    }
}

/** Отбор касс выполняется в SQL, включая доступ владельца ко всем филиалам. */
internal const val CASH_ACCESS_SQL = """(c.branch_id IS NULL OR EXISTS (
    SELECT 1 FROM employees access_employee WHERE access_employee.id = :employee AND access_employee.org_id = :org AND
    (access_employee.is_owner OR access_employee.all_branches_access OR EXISTS (
        SELECT 1 FROM employee_branches eb WHERE eb.employee_id = access_employee.id AND eb.branch_id = c.branch_id
    ))
))"""

/** Все кассы исходной операции должны быть доступны для её отмены. */
internal val TRANSACTION_CASH_ACCESS_SQL = """NOT EXISTS (
    SELECT 1 FROM ledger_postings cash_p JOIN ledger_accounts cash_a ON cash_a.id = cash_p.account_id AND cash_a.kind = 'cash' AND cash_a.org_id = :org
    JOIN cash_accounts c ON c.id = cash_a.cash_account_id AND c.org_id = :org
    WHERE cash_p.transaction_id = t.id AND NOT $CASH_ACCESS_SQL
)"""
