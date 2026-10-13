package org.athletica.crm.read.employees

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.employees.EmployeeListResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/**
 * Read-проекция списка сотрудников организации для раздела «Сотрудники»:
 * сотрудники с названиями назначенных ролей одним запросом.
 */
interface EmployeeListView {
    /** Возвращает всех сотрудников организации: сначала владелец, затем по имени. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(): EmployeeListResponse
}
