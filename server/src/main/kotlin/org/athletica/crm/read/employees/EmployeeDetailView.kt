package org.athletica.crm.read.employees

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.employees.EmployeeDetailResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/**
 * Read-проекция карточки сотрудника: контакты, роли, персональные выдачи и отзывы прав,
 * доступные филиалы — одним запросом.
 */
interface EmployeeDetailView {
    /** Возвращает карточку сотрудника [id]; ошибка `EMPLOYEE_NOT_FOUND`, если его нет в организации. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byId(id: EmployeeId): EmployeeDetailResponse
}
