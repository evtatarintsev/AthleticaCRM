package org.athletica.crm.read.finance

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.finance.SaleDetailResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.SaleId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString

/** Документ продажи с вычисленными суммами и историей оплат и возвратов одним SQL. */
interface SaleDetailView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun byId(id: SaleId): SaleDetailResponse
}

class DbSaleDetailView : SaleDetailView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun byId(id: SaleId): SaleDetailResponse =
        tr.sql(SQL).bind("sale", id).bind("org", ctx.orgId).bind("currency", ctx.currency.code).bind("actor", ctx.employee.id).bind("employee", ctx.employee.id)
            .bind("canManage", ctx.hasPermission(org.athletica.crm.core.permissions.UserPermission.CAN_MANAGE_FINANCE))
            .bind("canAccept", ctx.hasPermission(org.athletica.crm.core.permissions.UserPermission.CAN_ACCEPT_PAYMENTS))
            .firstOrNull { Json.decodeFromString<SaleDetailResponse>(it.asString("response")) }
            ?: raise(CommonDomainError("SALE_NOT_FOUND", "Sale not found"))

    private companion object {
        val SQL =
            """
            WITH amounts AS (
                SELECT s.*, COALESCE((SELECT SUM(i.amount) FROM sale_items i WHERE i.sale_id = s.id), 0) AS total,
                COALESCE((SELECT -SUM(p.amount) FROM ledger_transactions t JOIN ledger_postings p ON p.transaction_id = t.id
                    JOIN ledger_accounts a ON a.id = p.account_id AND a.kind = 'receivable'
                    WHERE t.sale_id = s.id AND t.org_id = s.org_id AND t.kind = 'payment'
                    AND NOT EXISTS (SELECT 1 FROM ledger_transactions r WHERE r.reverses_id = t.id)), 0) AS paid,
                COALESCE((SELECT SUM(p.amount) FROM ledger_transactions t JOIN ledger_postings p ON p.transaction_id = t.id
                    JOIN ledger_accounts a ON a.id = p.account_id AND a.kind = 'revenue'
                    WHERE t.sale_id = s.id AND t.org_id = s.org_id AND t.kind = 'sale_return'
                    AND NOT EXISTS (SELECT 1 FROM ledger_transactions r WHERE r.reverses_id = t.id)), 0) AS returned,
                GREATEST(COALESCE((SELECT SUM(p.amount) FROM ledger_accounts a JOIN ledger_postings p ON p.account_id = a.id
                    WHERE a.sale_id = s.id AND a.org_id = s.org_id AND a.kind = 'receivable'), 0), 0) AS debt
                FROM sales s WHERE s.id = :sale AND s.org_id = :org
            )
            SELECT jsonb_build_object(
                'id', s.id, 'clientId', s.client_id, 'soldOn', s.sold_on,
                'performedBy', CASE WHEN e.id IS NULL THEN NULL ELSE jsonb_build_object('id', e.id, 'name', e.name) END,
                'total', ${moneyJsonSql("s.total")}, 'paid', ${moneyJsonSql("s.paid")}, 'returned', ${moneyJsonSql("s.returned")}, 'debt', ${moneyJsonSql("s.debt")},
                'paymentState', CASE WHEN s.debt = 0 THEN 'PAID' WHEN s.paid = 0 THEN 'UNPAID' ELSE 'PARTIAL' END,
                'cancelledAt', s.cancelled_at, 'cancelReason', s.cancel_reason,
                'items', (SELECT jsonb_agg(jsonb_build_object('id', i.id, 'kind', upper(i.kind), 'name', i.name, 'quantity', i.quantity,
                    'unitPrice', ${moneyJsonSql("i.unit_price")}, 'amount', ${moneyJsonSql("i.amount")}) ORDER BY i.id) FROM sale_items i WHERE i.sale_id = s.id),
                'operations', COALESCE((SELECT jsonb_agg(jsonb_build_object(
                    'id', t.id, 'kind', upper(t.kind), 'occurredOn', t.occurred_on,
                    'amount', ${moneyJsonSql("COALESCE((SELECT SUM(abs(p.amount)) FROM ledger_postings p JOIN ledger_accounts a ON a.id = p.account_id WHERE p.transaction_id = t.id AND ((t.kind = 'payment' AND a.kind = 'cash') OR (t.kind = 'sale_return' AND a.kind = 'revenue') OR (t.kind = 'payout' AND a.kind = 'cash'))), 0) + CASE WHEN t.kind = 'payment' THEN t.advance_used ELSE 0 END")},
                    'cash', COALESCE((SELECT jsonb_agg(jsonb_build_object('cashAccountId', a.cash_account_id, 'cashAccountName', c.name, 'amount', ${moneyJsonSql("abs(p.amount)")})) FROM ledger_postings p JOIN ledger_accounts a ON a.id = p.account_id AND a.kind = 'cash' JOIN cash_accounts c ON c.id = a.cash_account_id AND c.org_id = s.org_id WHERE p.transaction_id = t.id), '[]'),
                    'fromAdvance', ${moneyJsonSql("t.advance_used")},
                    'performedBy', CASE WHEN actor.id IS NULL THEN NULL ELSE jsonb_build_object('id', actor.id, 'name', actor.name) END,
                    'comment', t.comment, 'reversed', EXISTS (SELECT 1 FROM ledger_transactions r WHERE r.reverses_id = t.id),
                    'canReverse', $TRANSACTION_CASH_ACCESS_SQL AND NOT EXISTS (SELECT 1 FROM ledger_transactions r WHERE r.reverses_id = t.id) AND (
                        (:canManage AND t.kind IN ('payment', 'payout')) OR
                        (:canAccept AND t.kind = 'payment' AND t.created_by = :actor AND
                            (t.created_at AT TIME ZONE (SELECT timezone FROM organizations WHERE id = :org))::date =
                            (CURRENT_TIMESTAMP AT TIME ZONE (SELECT timezone FROM organizations WHERE id = :org))::date)
                    )
                ) ORDER BY t.occurred_on DESC, t.created_at DESC, t.id DESC)
                FROM ledger_transactions t LEFT JOIN employees actor ON actor.id = t.created_by AND actor.org_id = t.org_id
                WHERE t.sale_id = s.id AND t.org_id = s.org_id AND t.kind IN ('payment', 'sale_return', 'payout') AND $TRANSACTION_CASH_ACCESS_SQL), '[]')
            )::text AS response FROM amounts s LEFT JOIN employees e ON e.id = s.created_by AND e.org_id = s.org_id
            """.trimIndent()
    }
}
