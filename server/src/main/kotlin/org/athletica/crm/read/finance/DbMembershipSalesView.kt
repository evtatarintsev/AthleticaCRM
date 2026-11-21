package org.athletica.crm.read.finance

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.memberships.MembershipSaleSchema
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.toMembershipId
import org.athletica.crm.core.entityids.toSaleId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.finance.SalePaymentState
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asMoney
import org.athletica.crm.storage.asUuid

/** [MembershipSalesView] одним SQL: оплачено — неотменённые оплаты, долг — остаток счёта долга. */
class DbMembershipSalesView : MembershipSalesView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun byClient(clientId: ClientId): Map<MembershipId, MembershipSaleSchema> =
        tr.sql(
            """
            SELECT m.id, s.id AS sale_id,
                COALESCE((SELECT -SUM(p.amount) FROM ledger_transactions t JOIN ledger_postings p ON p.transaction_id = t.id
                    JOIN ledger_accounts a ON a.id = p.account_id AND a.kind = 'receivable'
                    WHERE t.sale_id = s.id AND t.org_id = s.org_id AND t.kind = 'payment'
                    AND NOT EXISTS (SELECT 1 FROM ledger_transactions r WHERE r.reverses_id = t.id)), 0) AS paid,
                GREATEST(COALESCE((SELECT SUM(p.amount) FROM ledger_accounts a JOIN ledger_postings p ON p.account_id = a.id
                    WHERE a.sale_id = s.id AND a.org_id = s.org_id AND a.kind = 'receivable'), 0), 0) AS debt
            FROM memberships m JOIN sale_items i ON i.id = m.sale_item_id JOIN sales s ON s.id = i.sale_id AND s.org_id = m.org_id
            WHERE m.org_id = :org AND m.client_id = :client ORDER BY m.id
            """.trimIndent(),
        ).bind("org", ctx.orgId).bind("client", clientId).list {
            val paid = it.asMoney("paid", ctx.currency)
            val debt = it.asMoney("debt", ctx.currency)
            it.asUuid("id").toMembershipId() to
                MembershipSaleSchema(
                    it.asUuid("sale_id").toSaleId(), paid, debt,
                    if (debt.isZero) {
                        SalePaymentState.PAID
                    } else if (paid.isZero) {
                        SalePaymentState.UNPAID
                    } else {
                        SalePaymentState.PARTIAL
                    },
                )
        }.toMap()
}
