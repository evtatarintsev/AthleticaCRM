package org.athletica.crm.core.permissions

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.i18n.Messages

/** Проверяет у сотрудника из [ctx] право `CAN_MANAGE_CLIENT_APP`; иначе ошибка `PERMISSION_DENIED`. */
context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
fun requireManageClientApp() {
    ensure(ctx.hasPermission(UserPermission.CAN_MANAGE_CLIENT_APP)) {
        CommonDomainError("PERMISSION_DENIED", Messages.ClientAppManageDenied.localize())
    }
}
