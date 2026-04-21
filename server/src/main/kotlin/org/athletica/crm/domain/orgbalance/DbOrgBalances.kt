package org.athletica.crm.domain.orgbalance

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.clients.PerformedBy
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asDouble
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull

class DbOrgBalances {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun get(): OrgBalance {
        val entries =
            tr
                .sql(
                    """
                    SELECT j.id,
                           j.amount,
                           j.balance_after,
                           j.operation_type,
                           j.payment_method,
                           j.description,
                           j.created_at,
                           j.performed_by  AS performed_by_id,
                           e.name          AS performed_by_name
                    FROM org_balance_journal j
                    LEFT JOIN employees e ON e.user_id = j.performed_by AND e.org_id = j.org_id
                    WHERE j.org_id = :orgId
                    ORDER BY j.created_at DESC
                    """.trimIndent(),
                )
                .bind("orgId", ctx.orgId)
                .list { row ->
                    val performedById = row.asUuidOrNull("performed_by_id")
                    val performedByName = row.asStringOrNull("performed_by_name")
                    OrgBalanceEntry(
                        id = row.asUuid("id"),
                        amount = row.asDouble("amount"),
                        balanceAfter = row.asDouble("balance_after"),
                        operationType = row.asString("operation_type"),
                        paymentMethod = row.asStringOrNull("payment_method"),
                        description = row.asStringOrNull("description"),
                        performedBy =
                            if (performedById != null && performedByName != null) {
                                PerformedBy(id = performedById, name = performedByName)
                            } else {
                                null
                            },
                        createdAt = row.asInstant("created_at"),
                    )
                }

        return DbOrgBalance(entries, totalAmount = entries.sumOf { it.amount })
    }
}
