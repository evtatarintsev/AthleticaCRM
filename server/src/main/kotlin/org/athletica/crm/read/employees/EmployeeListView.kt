package org.athletica.crm.read.employees

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.employees.EmployeeListResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction
import kotlin.time.Instant

/**
 * Read-проекция списка сотрудников организации для раздела «Сотрудники»:
 * сотрудники с названиями назначенных ролей одним запросом.
 */
interface EmployeeListView {
    /** Возвращает всех сотрудников организации по запросу [query]: сначала владелец, затем по имени. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun list(query: EmployeeListQuery): EmployeeListResponse
}

/** Запрос списка сотрудников. */
data class EmployeeListQuery(
    /** Момент запроса, относительно которого считается признак «в сети». */
    val now: Instant,
)
