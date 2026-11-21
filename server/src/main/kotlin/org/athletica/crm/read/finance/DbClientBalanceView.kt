package org.athletica.crm.read.finance

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.money.Money
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asMoney

/** [ClientBalanceView] одним SQL по счетам аванса и долга клиента. */
class DbClientBalanceView : ClientBalanceView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun byClient(clientId: ClientId): Money =
        tr.sql(
            """
            SELECT -COALESCE(SUM(p.amount), 0) AS balance FROM clients c
            LEFT JOIN ledger_accounts a ON a.client_id = c.id AND a.org_id = c.org_id AND a.kind IN ('advance', 'receivable')
            LEFT JOIN ledger_postings p ON p.account_id = a.id
            WHERE c.id = :client AND c.org_id = :org GROUP BY c.id
            """.trimIndent(),
        ).bind("client", clientId).bind("org", ctx.orgId).firstOrNull { it.asMoney("balance", ctx.currency) }
            ?: raise(CommonDomainError("CLIENT_NOT_FOUND", Messages.ClientNotFound.localize()))
}
