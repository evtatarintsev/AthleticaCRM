package org.athletica.crm.domain.employees

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

interface Employees {
    /** Создаёт нового сотрудника. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun new(
        id: EmployeeId,
        name: String,
        phoneNo: String?,
        email: EmailAddress?,
        avatarId: UploadId?,
        permissions: EmployeePermission,
        availableBranches: EmployeeBranchAccess = EmployeeBranchAccess.All,
    ): Employee

    /** Возвращает одного сотрудника по идентификатору; ошибка `EMPLOYEE_NOT_FOUND` если не найден. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byId(id: EmployeeId): Employee

    /**
     * Возвращает список сотрудников по идентификаторам.
     * Порядок результата не гарантирован; дубликаты в [ids] игнорируются.
     * Ошибка `EMPLOYEE_NOT_FOUND` если хотя бы один из запрошенных id не найден в организации.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byIds(ids: List<EmployeeId>): List<Employee>

    /** Возвращает всех сотрудников организации. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(): List<Employee>
}
