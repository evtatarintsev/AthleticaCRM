package org.athletica.crm.core.permissions

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.i18n.LocalizationKey

/** Проверяет у сотрудника из [ctx] право `CAN_MANAGE_WIDGETS`; иначе ошибка `PERMISSION_DENIED`. */
context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
fun requireManageWidgets() {
    ensure(ctx.hasPermission(UserPermission.CAN_MANAGE_WIDGETS)) {
        CommonDomainError("PERMISSION_DENIED", WidgetsAccessDenied.localize())
    }
}

private object WidgetsAccessDenied : LocalizationKey {
    override val ru = "Недостаточно прав для управления виджетами"
    override val en = "You do not have permission to manage widgets"
}
