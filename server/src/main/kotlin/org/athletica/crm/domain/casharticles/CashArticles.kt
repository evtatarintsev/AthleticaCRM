package org.athletica.crm.domain.casharticles

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.CashArticleId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.toCashArticleId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.finance.CashDirection
import org.athletica.crm.core.permissions.UserPermission.CAN_MANAGE_FINANCE
import org.athletica.crm.core.permissions.requireFinancePermission
import org.athletica.crm.domain.ledger.ledgerError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull

/** Статья ДДС; группа имеет только один уровень дочерних статей. */
data class CashArticle(
    val id: CashArticleId,
    val name: String,
    val direction: CashDirection,
    val parentId: CashArticleId?,
    val isGroup: Boolean,
    val systemCode: String?,
    val archived: Boolean,
)

class CashArticles {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun byId(id: CashArticleId): CashArticle =
        tr.sql("SELECT * FROM cash_articles WHERE id = :id AND org_id = :org")
            .bind("id", id).bind("org", ctx.orgId).firstOrNull {
                CashArticle(id, it.asString("name"), CashDirection.valueOf(it.asString("direction").uppercase()), it.asUuidOrNull("parent_id")?.toCashArticleId(), it.asBoolean("is_group"), it.asStringOrNull("system_code"), it.asBoolean("archived"))
            } ?: raise(ledgerError("CASH_ARTICLE_NOT_FOUND"))

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun system(code: String): CashArticleId =
        tr.sql("SELECT id FROM cash_articles WHERE org_id = :org AND system_code = :code").bind("org", ctx.orgId).bind("code", code)
            .firstOrNull { it.asUuid("id").toCashArticleId() } ?: raise(ledgerError("CASH_ARTICLE_NOT_FOUND"))

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun save(
        id: CashArticleId,
        name: String,
        direction: CashDirection,
        parentId: CashArticleId?,
        isGroup: Boolean,
    ) {
        requireFinancePermission(CAN_MANAGE_FINANCE)
        lockCatalog()
        ensure(name.isNotBlank() && name.trim().length <= 255) { ledgerError("FINANCE_NAME_REQUIRED") }
        ensure(parentId != id && !(isGroup && parentId != null)) { ledgerError("CASH_ARTICLE_DEPTH") }
        if (parentId != null) {
            val parent = byId(parentId)
            ensure(parent.isGroup && parent.parentId == null && !parent.archived && parent.systemCode == null && parent.direction == direction) { ledgerError("CASH_ARTICLE_PARENT_INVALID") }
        }
        val exists = tr.sql("SELECT id FROM cash_articles WHERE id = :id AND org_id = :org").bind("id", id).bind("org", ctx.orgId).firstOrNull { 1 } != null
        if (exists) {
            val old = byId(id)
            ensure(old.systemCode == null) { ledgerError("CASH_ARTICLE_SYSTEM_IMMUTABLE") }
            val children = tr.sql("SELECT id FROM cash_articles WHERE org_id = :org AND parent_id = :id").bind("org", ctx.orgId).bind("id", id).list { 1 }
            ensure(children.isEmpty() || (isGroup && parentId == null && direction == old.direction)) { ledgerError("CASH_ARTICLE_HAS_CHILDREN") }
            tr.sql("UPDATE cash_articles SET name = :name, direction = :direction, parent_id = :parent, is_group = :group WHERE id = :id AND org_id = :org")
                .bind("name", name.trim()).bind("direction", direction.name.lowercase()).bind("parent", parentId).bind("group", isGroup).bind("id", id).bind("org", ctx.orgId)
                .onViolation("uq_cash_articles_name") { nameTaken() }.execute()
        } else {
            tr.sql("INSERT INTO cash_articles (id, org_id, name, direction, parent_id, is_group) VALUES (:id, :org, :name, :direction, :parent, :group)")
                .bind("name", name.trim()).bind("direction", direction.name.lowercase()).bind("parent", parentId).bind("group", isGroup).bind("id", id).bind("org", ctx.orgId)
                .onViolation("uq_cash_articles_name") { nameTaken() }.onViolation("cash_articles_pkey") { ledgerError("CASH_ARTICLE_NOT_FOUND") }.execute()
        }
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun archive(id: CashArticleId, archived: Boolean) {
        requireFinancePermission(CAN_MANAGE_FINANCE)
        lockCatalog()
        val article = byId(id)
        ensure(article.systemCode == null) { ledgerError("CASH_ARTICLE_SYSTEM_IMMUTABLE") }
        if (archived) {
            ensure(tr.sql("SELECT id FROM cash_articles WHERE org_id = :org AND parent_id = :id AND NOT archived").bind("id", id).bind("org", ctx.orgId).firstOrNull { 1 } == null) { ledgerError("CASH_ARTICLE_ACTIVE_CHILDREN") }
        } else if (article.parentId != null) {
            ensure(!byId(article.parentId).archived) { ledgerError("CASH_ARTICLE_PARENT_INVALID") }
        }
        tr.sql("UPDATE cash_articles SET archived = :archived WHERE id = :id AND org_id = :org")
            .bind("archived", archived).bind("id", id).bind("org", ctx.orgId).onViolation("uq_cash_articles_name") { nameTaken() }.execute()
    }

    // Изменение дерева сериализуется по организации: иначе архив группы гоняется с созданием статьи.
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    private suspend fun lockCatalog() {
        tr.sql("SELECT id FROM organizations WHERE id = :org FOR UPDATE").bind("org", ctx.orgId).firstOrNull { 1 }
    }

    context(ctx: EmployeeRequestContext)
    private fun nameTaken() = CommonDomainError("CASH_ARTICLE_NAME_TAKEN", if (ctx.lang == Lang.RU) "Название статьи уже занято" else "Cash article name is already taken")
}

/** Начальные статьи создаются при регистрации той же транзакцией. */
context(tr: Transaction, raise: Raise<AppError>)
suspend fun seedCashArticles(orgId: OrgId, lang: Lang) {
    val names =
        if (lang == Lang.RU) {
            listOf("Поступления от клиентов", "Возвраты клиентам", "Прочие доходы", "Аренда", "Зарплата", "Налоги", "Реклама", "Инвентарь", "Комиссия эквайринга", "Прочие расходы")
        } else {
            listOf("Client receipts", "Client refunds", "Other income", "Rent", "Payroll", "Taxes", "Advertising", "Equipment", "Acquiring fees", "Other expenses")
        }
    names.forEachIndexed { i, name ->
        tr.sql("INSERT INTO cash_articles (org_id, name, direction, system_code) VALUES (:org, :name, :direction, :system)")
            .bind("org", orgId).bind("name", name).bind("direction", if (i == 0 || i == 2) "income" else "expense")
            .bind(
                "system",
                when (i) {
                    0 -> "client_receipts"
                    1 -> "client_refunds"
                    else -> null
                },
            ).execute()
    }
}
