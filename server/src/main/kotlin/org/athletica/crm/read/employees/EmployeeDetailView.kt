package org.athletica.crm.read.employees

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.employees.EmployeeDetailResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction
import kotlin.time.Instant

/**
 * Read-проекция карточки сотрудника: контакты, роли, персональные выдачи и отзывы прав,
 * доступные филиалы — одним запросом.
 */
interface EmployeeDetailView {
    /** Возвращает карточку сотрудника по запросу [query]; ошибка `EMPLOYEE_NOT_FOUND`, если его нет в организации. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun byId(query: EmployeeDetailQuery): EmployeeDetailResponse
}

/** Запрос карточки сотрудника. */
data class EmployeeDetailQuery(
    /** Сотрудник. */
    val id: EmployeeId,
    /** Момент запроса, относительно которого считается признак «в сети». */
    val now: Instant,
)
