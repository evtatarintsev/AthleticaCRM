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
import org.athletica.crm.i18n.LocalizationKey
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull

/** Статья ДДС; группа имеет только один уровень дочерних статей. */
data class CashArticle(
    /** Идентификатор статьи. */
    val id: CashArticleId,
    /** Название статьи. */
    val name: String,
    /** Направление движения денег: приход или расход. */
    val direction: CashDirection,
    /** Группа, в которую входит статья; `null` для статьи верхнего уровня. */
    val parentId: CashArticleId?,
    /** Признак группы статей. */
    val isGroup: Boolean,
    /** Код системной статьи; системные статьи не редактируются и не архивируются. */
    val systemCode: String?,
    /** Признак архивной статьи. */
    val archived: Boolean,
)

/**
 * Справочник статей ДДС организации. Команды изменения дерева сериализуются по организации,
 * чтобы архив группы не гонялся с созданием дочерней статьи.
 */
class CashArticles {
    /** Статья [id] организации из контекста; если её нет — ошибка. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun byId(id: CashArticleId): CashArticle =
        tr.sql("SELECT * FROM cash_articles WHERE id = :id AND org_id = :org")
            .bind("id", id).bind("org", ctx.orgId).firstOrNull {
                CashArticle(id, it.asString("name"), CashDirection.valueOf(it.asString("direction").uppercase()), it.asUuidOrNull("parent_id")?.toCashArticleId(), it.asBoolean("is_group"), it.asStringOrNull("system_code"), it.asBoolean("archived"))
            } ?: raise(ledgerError("CASH_ARTICLE_NOT_FOUND"))

    /** Идентификатор системной статьи с кодом [code]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun system(code: String): CashArticleId =
        tr.sql("SELECT id FROM cash_articles WHERE org_id = :org AND system_code = :code").bind("org", ctx.orgId).bind("code", code)
            .firstOrNull { it.asUuid("id").toCashArticleId() } ?: raise(ledgerError("CASH_ARTICLE_NOT_FOUND"))

    /**
     * Создаёт статью [id] или изменяет существующую: название [name], направление [direction],
     * группа [parentId] и признак группы [isGroup]. Направление и признак группы у статьи с
     * движениями не меняются: иначе ДДС прошлых периодов изменил бы знак или разнёс суммы на группу.
     */
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
            if (direction != old.direction || isGroup != old.isGroup) {
                ensure(!hasMovements(id)) { ledgerError("CASH_ARTICLE_HAS_MOVEMENTS") }
            }
            tr.sql("UPDATE cash_articles SET name = :name, direction = :direction, parent_id = :parent, is_group = :group WHERE id = :id AND org_id = :org")
                .bind("name", name.trim()).bind("direction", direction.name.lowercase()).bind("parent", parentId).bind("group", isGroup).bind("id", id).bind("org", ctx.orgId)
                .onViolation("uq_cash_articles_name") { nameTaken() }.execute()
        } else {
            tr.sql("INSERT INTO cash_articles (id, org_id, name, direction, parent_id, is_group) VALUES (:id, :org, :name, :direction, :parent, :group)")
                .bind("name", name.trim()).bind("direction", direction.name.lowercase()).bind("parent", parentId).bind("group", isGroup).bind("id", id).bind("org", ctx.orgId)
                .onViolation("uq_cash_articles_name") { nameTaken() }.onViolation("cash_articles_pkey") { ledgerError("CASH_ARTICLE_NOT_FOUND") }.execute()
        }
    }

    /** Переводит статью [id] в архив или возвращает из него по флагу [archived]. */
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

    /**
     * Были ли движения по статье [id]. Каждая операция по несистемной статье заводит её счёт
     * прихода или расхода в главной книге, поэтому наличия счёта достаточно.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    private suspend fun hasMovements(id: CashArticleId): Boolean =
        tr.sql("SELECT id FROM ledger_accounts WHERE org_id = :org AND article_id = :id LIMIT 1")
            .bind("org", ctx.orgId).bind("id", id).firstOrNull { 1 } != null

    /** Блокирует организацию, сериализуя изменения дерева статей. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    private suspend fun lockCatalog() {
        tr.sql("SELECT id FROM organizations WHERE id = :org FOR UPDATE").bind("org", ctx.orgId).firstOrNull { 1 }
    }

    /** Ошибка занятого названия статьи. */
    context(ctx: EmployeeRequestContext)
    private fun nameTaken() = CommonDomainError("CASH_ARTICLE_NAME_TAKEN", CashArticleNameTaken.localize())
}

private object CashArticleNameTaken : LocalizationKey {
    override val ru = "Название статьи уже занято"
    override val en = "Cash article name is already taken"
}

/** Начальная статья справочника: название на двух языках, направление и системный код. */
private data class SeedArticle(
    val ru: String,
    val en: String,
    val direction: CashDirection,
    val systemCode: String? = null,
)

private val SEED_ARTICLES =
    listOf(
        SeedArticle("Поступления от клиентов", "Client receipts", CashDirection.INCOME, "client_receipts"),
        SeedArticle("Возвраты клиентам", "Client refunds", CashDirection.EXPENSE, "client_refunds"),
        SeedArticle("Прочие доходы", "Other income", CashDirection.INCOME),
        SeedArticle("Аренда", "Rent", CashDirection.EXPENSE),
        SeedArticle("Зарплата", "Payroll", CashDirection.EXPENSE),
        SeedArticle("Налоги", "Taxes", CashDirection.EXPENSE),
        SeedArticle("Реклама", "Advertising", CashDirection.EXPENSE),
        SeedArticle("Инвентарь", "Equipment", CashDirection.EXPENSE),
        SeedArticle("Комиссия эквайринга", "Acquiring fees", CashDirection.EXPENSE),
        SeedArticle("Прочие расходы", "Other expenses", CashDirection.EXPENSE),
    )

/** Создаёт начальные статьи организации [orgId] на языке [lang] в транзакции регистрации. */
context(tr: Transaction, raise: Raise<AppError>)
suspend fun seedCashArticles(orgId: OrgId, lang: Lang) {
    SEED_ARTICLES.forEach { article ->
        tr.sql("INSERT INTO cash_articles (org_id, name, direction, system_code) VALUES (:org, :name, :direction, :system)")
            .bind("org", orgId).bind("name", if (lang == Lang.RU) article.ru else article.en)
            .bind("direction", article.direction.name.lowercase()).bind("system", article.systemCode).execute()
    }
}
