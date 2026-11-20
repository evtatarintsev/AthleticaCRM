package org.athletica.crm.read.finance

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import org.athletica.crm.api.schemas.finance.CashAccountBalancesResponse
import org.athletica.crm.api.schemas.finance.CashAccountSchema
import org.athletica.crm.api.schemas.finance.CashArticleSchema
import org.athletica.crm.api.schemas.finance.CashArticlesResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.toBranchId
import org.athletica.crm.core.entityids.toCashAccountId
import org.athletica.crm.core.entityids.toCashArticleId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.finance.CashAccountKind
import org.athletica.crm.core.finance.CashDirection
import org.athletica.crm.core.money.sum
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asMoney
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull

/** Остатки всех доступных касс, включая архивные, одним SQL. */
interface CashAccountBalancesView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun all(): CashAccountBalancesResponse
}

class DbCashAccountBalancesView : CashAccountBalancesView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun all(): CashAccountBalancesResponse {
        requireCatalogAccess()
        val rows =
            tr.sql(
                """
                SELECT c.*, COALESCE(SUM(p.amount), 0) AS balance FROM cash_accounts c
                LEFT JOIN ledger_accounts a ON a.cash_account_id = c.id AND a.org_id = c.org_id AND a.kind = 'cash'
                LEFT JOIN ledger_postings p ON p.account_id = a.id
                WHERE c.org_id = :org AND (c.branch_id IS NULL OR EXISTS (
                    SELECT 1 FROM employees e WHERE e.id = :employee AND e.org_id = :org AND
                    (e.is_owner OR e.all_branches_access OR EXISTS (SELECT 1 FROM employee_branches eb WHERE eb.employee_id = e.id AND eb.branch_id = c.branch_id))
                ))
                GROUP BY c.id ORDER BY c.archived, c.name, c.id
                """.trimIndent(),
            ).bind("org", ctx.orgId).bind("employee", ctx.employee.id).list {
                CashAccountSchema(it.asUuid("id").toCashAccountId(), it.asString("name"), CashAccountKind.valueOf(it.asString("kind").uppercase()), it.asUuidOrNull("branch_id")?.toBranchId(), it.asBoolean("archived"), it.asMoney("balance", ctx.currency))
            }
        return CashAccountBalancesResponse(rows, rows.map { it.balance }.sum(ctx.currency))
    }
}

interface CashArticlesView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun all(): CashArticlesResponse
}

class DbCashArticlesView : CashArticlesView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun all(): CashArticlesResponse {
        requireCatalogAccess()
        return CashArticlesResponse(
            tr.sql("SELECT * FROM cash_articles WHERE org_id = :org ORDER BY direction, archived, name, id").bind("org", ctx.orgId).list {
                CashArticleSchema(it.asUuid("id").toCashArticleId(), it.asString("name"), CashDirection.valueOf(it.asString("direction").uppercase()), it.asUuidOrNull("parent_id")?.toCashArticleId(), it.asBoolean("is_group"), it.asStringOrNull("system_code"), it.asBoolean("archived"))
            },
        )
    }
}

context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
private fun requireCatalogAccess() {
    ensure(listOf(UserPermission.CAN_ACCEPT_PAYMENTS, UserPermission.CAN_MANAGE_FINANCE, UserPermission.CAN_VIEW_FINANCE).any(ctx::hasPermission)) {
        CommonDomainError("ACCESS_DENIED", "Finance access denied")
    }
}
