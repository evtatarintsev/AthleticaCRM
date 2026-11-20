package org.athletica.crm.domain.cashaccounts

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.CashAccountId
import org.athletica.crm.core.entityids.toBranchId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.finance.CashAccountKind
import org.athletica.crm.core.finance.LedgerKind
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.permissions.UserPermission.CAN_MANAGE_FINANCE
import org.athletica.crm.core.permissions.requireFinancePermission
import org.athletica.crm.domain.ledger.Ledger
import org.athletica.crm.domain.ledger.LedgerEntries
import org.athletica.crm.domain.ledger.LedgerOwner
import org.athletica.crm.domain.ledger.ledgerError
import org.athletica.crm.domain.org.DbOrgCalendar
import org.athletica.crm.domain.org.OrgCalendar
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuidOrNull

/** Место хранения денег; архив сохраняет остаток и историю. */
data class CashAccount(
    val id: CashAccountId,
    val name: String,
    val kind: CashAccountKind,
    val branchId: BranchId?,
    val archived: Boolean,
)

/** Каталог касс: все команды проверяют организацию и доступ к филиалу. */
class CashAccounts(private val ledger: Ledger = Ledger(), private val calendar: OrgCalendar = DbOrgCalendar()) {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun byId(id: CashAccountId): CashAccount {
        val account =
            tr.sql("SELECT name, kind, branch_id, archived FROM cash_accounts WHERE id = :id AND org_id = :org")
                .bind("id", id).bind("org", ctx.orgId).firstOrNull {
                    CashAccount(id, it.asString("name"), CashAccountKind.valueOf(it.asString("kind").uppercase()), it.asUuidOrNull("branch_id")?.toBranchId(), it.asBoolean("archived"))
                } ?: raise(ledgerError("CASH_ACCOUNT_NOT_FOUND"))
        ensure(account.branchId == null || ctx.employee.availableBranches.contains(account.branchId)) { ledgerError("CASH_ACCOUNT_ACCESS_DENIED") }
        return account
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun activeById(id: CashAccountId): CashAccount {
        tr.sql("SELECT id FROM cash_accounts WHERE id = :id AND org_id = :org FOR SHARE")
            .bind("id", id).bind("org", ctx.orgId).firstOrNull { 1 }
        return byId(id).also { ensure(!it.archived) { ledgerError("CASH_ACCOUNT_ARCHIVED") } }
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun save(
        id: CashAccountId,
        name: String,
        kind: CashAccountKind,
        branchId: BranchId?,
        openingBalance: Money?,
        openingOn: LocalDate?,
    ) {
        requireFinancePermission(CAN_MANAGE_FINANCE)
        ensure(name.isNotBlank() && name.trim().length <= 255) { ledgerError("FINANCE_NAME_REQUIRED") }
        if (branchId != null) {
            ensure(ctx.employee.availableBranches.contains(branchId)) { ledgerError("CASH_ACCOUNT_ACCESS_DENIED") }
            ensure(tr.sql("SELECT id FROM branches WHERE id = :id AND org_id = :org").bind("id", branchId).bind("org", ctx.orgId).firstOrNull { 1 } != null) { ledgerError("BRANCH_NOT_FOUND") }
        }
        val exists = tr.sql("SELECT id FROM cash_accounts WHERE id = :id AND org_id = :org FOR UPDATE").bind("id", id).bind("org", ctx.orgId).firstOrNull { 1 } != null
        if (exists) {
            byId(id)
            ensure(openingBalance == null && openingOn == null) { ledgerError("CASH_OPENING_ONLY_ON_CREATE") }
            tr.sql("UPDATE cash_accounts SET name = :name, kind = :kind, branch_id = :branch WHERE id = :id AND org_id = :org")
                .bind("id", id).bind("org", ctx.orgId).bind("name", name.trim()).bind("kind", kind.name.lowercase()).bind("branch", branchId)
                .onViolation("uq_cash_accounts_name") { nameTaken() }.execute()
        } else {
            ensure(openingBalance == null || openingBalance.currency == ctx.currency) { ledgerError("LEDGER_CURRENCY_MISMATCH") }
            val date = openingOn ?: calendar.today()
            ensure(date <= calendar.today()) { ledgerError("FINANCE_FUTURE_DATE") }
            tr.sql("INSERT INTO cash_accounts (id, org_id, name, kind, branch_id) VALUES (:id, :org, :name, :kind, :branch)")
                .bind("id", id).bind("org", ctx.orgId).bind("name", name.trim()).bind("kind", kind.name.lowercase()).bind("branch", branchId)
                .onViolation("uq_cash_accounts_name") { nameTaken() }.onViolation("cash_accounts_pkey") { ledgerError("CASH_ACCOUNT_NOT_FOUND") }.execute()
            if (openingBalance != null && !openingBalance.isZero) {
                ledger.record(LedgerEntries.cash(LedgerKind.OPENING, ledger.account(LedgerOwner.Cash(id)), ledger.account(LedgerOwner.Opening), openingBalance), date)
            }
        }
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun archive(id: CashAccountId, archived: Boolean) {
        requireFinancePermission(CAN_MANAGE_FINANCE)
        byId(id)
        tr.sql("UPDATE cash_accounts SET archived = :archived WHERE id = :id AND org_id = :org")
            .bind("archived", archived).bind("id", id).bind("org", ctx.orgId).onViolation("uq_cash_accounts_name") { nameTaken() }.execute()
    }

    context(ctx: EmployeeRequestContext)
    private fun nameTaken() = CommonDomainError("CASH_ACCOUNT_NAME_TAKEN", if (ctx.lang == org.athletica.crm.core.Lang.RU) "Название кассы уже занято" else "Cash account name is already taken")
}
