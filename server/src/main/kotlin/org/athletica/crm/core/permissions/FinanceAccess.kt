package org.athletica.crm.core.permissions

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.i18n.LocalizationKey

/** Проверка финансового права перед выполнением команды или чтением проекции. */
context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
fun requireFinancePermission(permission: UserPermission) {
    ensure(ctx.hasPermission(permission)) { CommonDomainError("ACCESS_DENIED", FinanceAccessDenied.localize()) }
}

private object FinanceAccessDenied : LocalizationKey {
    override val ru = "Недостаточно прав для финансовой операции"
    override val en = "You do not have permission for this financial operation"
}
