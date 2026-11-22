package org.athletica.crm.demo

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.SystemContexts
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.toBranchId
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.entityids.toUserId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.domain.employees.Employees
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/**
 * Контекст владельца организации [orgId] на языке [lang] в её первом филиале — от его имени
 * наполнение пишет аудит, отметки и оплаты. Сотрудник загружается через [employees], как при входе.
 */
context(tr: Transaction, raise: Raise<AppError>)
suspend fun ownerContext(orgId: OrgId, employees: Employees, lang: Lang = Lang.RU): EmployeeRequestContext {
    val (employeeId, userId, login) =
        tr.sql(
            """
            SELECT e.id, e.user_id, u.login FROM employees e JOIN users u ON u.id = e.user_id
            WHERE e.org_id = :org AND e.is_owner ORDER BY e.joined_at, e.id LIMIT 1
            """.trimIndent(),
        ).bind("org", orgId)
            .firstOrNull { Triple(it.asUuid("id").toEmployeeId(), it.asUuid("user_id").toUserId(), it.asString("login")) }
            ?: raise(CommonDomainError("DEMO_OWNER_NOT_FOUND", Messages.DemoOwnerNotFound.localize(lang)))
    val branchId =
        tr.sql("SELECT id FROM branches WHERE org_id = :org ORDER BY created_at, id LIMIT 1")
            .bind("org", orgId)
            .firstOrNull { it.asUuid("id").toBranchId() }
            ?: raise(CommonDomainError("DEMO_OWNER_NOT_FOUND", Messages.DemoOwnerNotFound.localize(lang)))
    val system = SystemContexts.forOrg(orgId)
    val employee = context(system) { employees.byId(employeeId) }
    return EmployeeRequestContext(lang, orgId, system.currency, userId, branchId, login, null, employee)
}
