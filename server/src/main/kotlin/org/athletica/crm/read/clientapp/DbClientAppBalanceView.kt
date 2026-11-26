package org.athletica.crm.read.clientapp

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.clientapp.ClientAppBalanceResponse
import org.athletica.crm.core.ClientProfileContext
import org.athletica.crm.core.ClientProfileContexts
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.read.finance.moneyJsonSql
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString

/**
 * [ClientAppBalanceView] одним SQL по счетам аванса и долга клиента с проверкой подключённого доступа.
 * Неоплаченные занятия — проведённые с отметкой «пришёл» без действующего списания, во всех филиалах.
 */
class DbClientAppBalanceView : ClientAppBalanceView {
    context(ctx: ClientProfileContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun balance(): ClientAppBalanceResponse =
        tr.sql(SQL)
            .bind("accountId", ctx.accountId)
            .bind("orgId", ctx.orgId)
            .bind("clientId", ctx.clientId)
            .bind("currency", ctx.currency.code)
            .firstOrNull { Json.decodeFromString<ClientAppBalanceResponse>(it.asString("response")) }
            ?: raise(ClientProfileContexts.clientNotFound(ctx.lang))

    private companion object {
        val SQL =
            """
            WITH entries AS (
                SELECT t.id, t.kind, t.occurred_on, t.created_at, -SUM(p.amount) AS change
                FROM ledger_accounts a JOIN ledger_postings p ON p.account_id = a.id
                JOIN ledger_transactions t ON t.id = p.transaction_id AND t.org_id = a.org_id
                WHERE a.org_id = :orgId AND a.client_id = :clientId AND a.kind IN ('advance', 'receivable')
                GROUP BY t.id
            )
            SELECT jsonb_build_object(
                'balance', ${moneyJsonSql("COALESCE((SELECT SUM(change) FROM entries), 0)")},
                'unpaidSessions', (
                    SELECT COUNT(*) FROM session_attendance sa JOIN sessions s ON s.id = sa.session_id
                    WHERE sa.client_id = :clientId AND sa.presence = 'present' AND s.org_id = :orgId AND s.status = 'completed'
                      AND NOT EXISTS (SELECT 1 FROM membership_debits d WHERE d.session_id = s.id AND d.client_id = sa.client_id AND d.reversed_at IS NULL)
                ),
                'entries', COALESCE((SELECT jsonb_agg(jsonb_build_object(
                    'occurredOn', occurred_on, 'kind', upper(kind), 'amount', ${moneyJsonSql("change")}
                ) ORDER BY occurred_on DESC, created_at DESC, id DESC) FROM entries WHERE change <> 0), '[]')
            )::text AS response
            WHERE EXISTS (${ClientProfileContexts.CONNECTED_ACCESS_IDS})
            """.trimIndent()
    }
}
