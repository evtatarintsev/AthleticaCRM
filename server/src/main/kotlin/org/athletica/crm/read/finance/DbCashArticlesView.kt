package org.athletica.crm.read.finance

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.finance.CashArticleSchema
import org.athletica.crm.api.schemas.finance.CashArticlesResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.toCashArticleId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.finance.CashDirection
import org.athletica.crm.core.permissions.UserPermission.CAN_ACCEPT_PAYMENTS
import org.athletica.crm.core.permissions.UserPermission.CAN_MANAGE_FINANCE
import org.athletica.crm.core.permissions.UserPermission.CAN_VIEW_FINANCE
import org.athletica.crm.core.permissions.requireAnyFinancePermission
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull

/** [CashArticlesView] одним SQL по `cash_articles`. */
class DbCashArticlesView : CashArticlesView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun all(): CashArticlesResponse {
        requireAnyFinancePermission(CAN_ACCEPT_PAYMENTS, CAN_MANAGE_FINANCE, CAN_VIEW_FINANCE)
        return CashArticlesResponse(
            tr.sql("SELECT * FROM cash_articles WHERE org_id = :org ORDER BY direction, archived, name, id").bind("org", ctx.orgId).list {
                CashArticleSchema(it.asUuid("id").toCashArticleId(), it.asString("name"), CashDirection.valueOf(it.asString("direction").uppercase()), it.asUuidOrNull("parent_id")?.toCashArticleId(), it.asBoolean("is_group"), it.asStringOrNull("system_code"), it.asBoolean("archived"))
            },
        )
    }
}
