package org.athletica.crm.read.finance

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.finance.ClientLedgerResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.permissions.UserPermission.CAN_VIEW_CLIENT_BALANCE
import org.athletica.crm.core.permissions.requireFinancePermission
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString

/** [ClientLedgerView] одним SQL; баланс после операции считает оконная функция. */
class DbClientLedgerView : ClientLedgerView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun byClient(clientId: ClientId): ClientLedgerResponse {
        requireFinancePermission(CAN_VIEW_CLIENT_BALANCE)
        return clientLedger(clientId, includeHistory = true)
    }
}

/** Лицевой счёт клиента [clientId]; история операций включается флагом [includeHistory]. */
context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
internal suspend fun clientLedger(clientId: ClientId, includeHistory: Boolean): ClientLedgerResponse =
    tr.sql(CLIENT_LEDGER_SQL).bind("org", ctx.orgId).bind("client", clientId).bind("currency", ctx.currency.code).bind("history", includeHistory)
        .firstOrNull { Json.decodeFromString<ClientLedgerResponse>(it.asString("response")) }
        ?: raise(CommonDomainError("CLIENT_NOT_FOUND", Messages.ClientNotFound.localize()))

private val CLIENT_LEDGER_SQL =
    """
    WITH entries AS (
        SELECT t.*, -SUM(p.amount) AS change,
               t.advance_used AS from_advance,
               EXISTS (SELECT 1 FROM ledger_transactions r WHERE r.reverses_id = t.id) AS reversed,
               e.name AS employee_name
        FROM ledger_accounts a JOIN ledger_postings p ON p.account_id = a.id
        JOIN ledger_transactions t ON t.id = p.transaction_id AND t.org_id = a.org_id
        LEFT JOIN employees e ON e.id = t.created_by AND e.org_id = t.org_id
        WHERE a.org_id = :org AND a.client_id = :client AND a.kind IN ('advance', 'receivable')
        GROUP BY t.id, e.name
    ), history AS (
        SELECT *, SUM(change) OVER (ORDER BY occurred_on, created_at, id) AS balance_after FROM entries
    ), debts AS (
        SELECT s.id, s.sold_on, COALESCE(SUM(p.amount), 0) AS debt FROM sales s
        LEFT JOIN ledger_accounts a ON a.sale_id = s.id AND a.org_id = s.org_id AND a.kind = 'receivable'
        LEFT JOIN ledger_postings p ON p.account_id = a.id
        WHERE s.client_id = :client AND s.org_id = :org AND s.cancelled_at IS NULL
        GROUP BY s.id HAVING COALESCE(SUM(p.amount), 0) > 0
    ), summary AS (
        SELECT -COALESCE(SUM(p.amount) FILTER (WHERE a.kind = 'advance'), 0) AS advance,
                COALESCE(SUM(p.amount) FILTER (WHERE a.kind = 'receivable'), 0) AS debt
        FROM ledger_accounts a LEFT JOIN ledger_postings p ON p.account_id = a.id
        WHERE a.org_id = :org AND a.client_id = :client
    )
    SELECT jsonb_build_object(
        'balance', ${moneyJsonSql("advance - debt")}, 'advance', ${moneyJsonSql("advance")}, 'debt', ${moneyJsonSql("debt")},
        'entries', COALESCE((SELECT jsonb_agg(jsonb_build_object(
            'id', id, 'kind', upper(kind), 'occurredOn', occurred_on, 'createdAt', created_at,
            'amount', ${moneyJsonSql("change")}, 'balanceAfter', ${moneyJsonSql("balance_after")},
            'fromAdvance', ${moneyJsonSql("from_advance")}, 'performedBy', CASE WHEN employee_name IS NULL THEN NULL ELSE jsonb_build_object('id', created_by, 'name', employee_name) END,
            'comment', comment, 'saleId', sale_id, 'reversed', reversed
        ) ORDER BY occurred_on DESC, created_at DESC, id DESC) FROM history WHERE :history), '[]'),
        'salesWithDebt', COALESCE((SELECT jsonb_agg(jsonb_build_object('saleId', id, 'soldOn', sold_on, 'debt', ${moneyJsonSql("debt")}) ORDER BY sold_on, id) FROM debts), '[]')
    )::text AS response FROM summary WHERE EXISTS (SELECT 1 FROM clients WHERE id = :client AND org_id = :org)
    """.trimIndent()
