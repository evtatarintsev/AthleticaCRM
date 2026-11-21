package org.athletica.crm.core.permissions

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.i18n.LocalizationKey

/** Проверяет у сотрудника из [ctx] финансовое право [permission] перед командой или чтением проекции. */
context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
fun requireFinancePermission(permission: UserPermission) {
    requireAnyFinancePermission(permission)
}

/** Проверяет, что у сотрудника из [ctx] есть хотя бы одно из финансовых прав [permissions]. */
context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
fun requireAnyFinancePermission(vararg permissions: UserPermission) {
    ensure(permissions.any { ctx.hasPermission(it) }) { financeAccessDenied() }
}

/** Ошибка отказа в финансовой операции. */
context(ctx: EmployeeRequestContext)
fun financeAccessDenied() = CommonDomainError("PERMISSION_DENIED", FinanceAccessDenied.localize())

private object FinanceAccessDenied : LocalizationKey {
    override val ru = "Недостаточно прав для финансовой операции"
    override val en = "You do not have permission for this financial operation"
}
