package org.athletica.crm.read.finance

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.finance.CashAccountBalancesResponse
import org.athletica.crm.api.schemas.finance.CashAccountSchema
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.toBranchId
import org.athletica.crm.core.entityids.toCashAccountId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.finance.CashAccountKind
import org.athletica.crm.core.money.sum
import org.athletica.crm.core.permissions.UserPermission.CAN_ACCEPT_PAYMENTS
import org.athletica.crm.core.permissions.UserPermission.CAN_MANAGE_FINANCE
import org.athletica.crm.core.permissions.UserPermission.CAN_VIEW_FINANCE
import org.athletica.crm.core.permissions.requireAnyFinancePermission
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asMoney
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull

/** [CashAccountBalancesView] одним SQL: остатки — сумма проводок по счёту кассы. */
class DbCashAccountBalancesView : CashAccountBalancesView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun all(): CashAccountBalancesResponse {
        requireAnyFinancePermission(CAN_ACCEPT_PAYMENTS, CAN_MANAGE_FINANCE, CAN_VIEW_FINANCE)
        val rows =
            tr.sql(
                """
                SELECT c.*, COALESCE(SUM(p.amount), 0) AS balance FROM cash_accounts c
                LEFT JOIN ledger_accounts a ON a.cash_account_id = c.id AND a.org_id = c.org_id AND a.kind = 'cash'
                LEFT JOIN ledger_postings p ON p.account_id = a.id
                WHERE c.org_id = :org AND $CASH_ACCESS_SQL
                GROUP BY c.id ORDER BY c.archived, c.name, c.id
                """.trimIndent(),
            ).bind("org", ctx.orgId).bind("employee", ctx.employee.id).list {
                CashAccountSchema(it.asUuid("id").toCashAccountId(), it.asString("name"), CashAccountKind.valueOf(it.asString("kind").uppercase()), it.asUuidOrNull("branch_id")?.toBranchId(), it.asBoolean("archived"), it.asMoney("balance", ctx.currency))
            }
        return CashAccountBalancesResponse(rows, rows.map { it.balance }.sum(ctx.currency))
    }
}
