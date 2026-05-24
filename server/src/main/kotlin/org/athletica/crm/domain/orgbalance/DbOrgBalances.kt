package org.athletica.crm.domain.orgbalance

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.sum
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asMoney
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

class DbOrgBalances : OrgBalances {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun current(): OrgBalance {
        val entries =
            tr
                .sql(
                    """
                    SELECT j.id,
                           j.amount,
                           j.balance_after,
                           j.operation_type,
                           j.description,
                           j.created_at
                    FROM org_balance_journal j
                    WHERE j.org_id = :orgId
                    ORDER BY j.created_at DESC
                    """.trimIndent(),
                )
                .bind("orgId", ctx.orgId)
                .list { row ->
                    OrgBalanceEntry(
                        id = row.asUuid("id"),
                        amount = row.asMoney("amount", ctx.currency),
                        balanceAfter = row.asMoney("balance_after", ctx.currency),
                        operationType = row.asString("operation_type"),
                        description = row.asString("description"),
                        createdAt = row.asInstant("created_at"),
                    )
                }

        return DbOrgBalance(
            totalAmount = entries.map { it.amount }.sum(ctx.currency),
            history = entries,
        )
    }
}
