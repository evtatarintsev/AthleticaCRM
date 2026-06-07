package org.athletica.crm.domain.branch

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/** Каталог филиалов организации. */
interface Branches {
    /**
     * Возвращает филиалы, доступные текущему сотруднику.
     * Если у сотрудника [org.athletica.crm.domain.employees.EmployeeBranchAccess.All] — все филиалы организации;
     * иначе — только явно назначенные через `employee_branches`.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction)
    suspend fun list(): List<Branch>

    /** Создаёт несохранённый филиал; запись в БД выполняется через [Branch.save]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun new(id: BranchId, name: String): Branch

    /** Возвращает филиал по идентификатору; ошибка `BRANCH_NOT_FOUND`, если не найден. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byId(id: BranchId): Branch

    /**
     * Возвращает филиалы по идентификаторам.
     * Дубликаты в [ids] игнорируются; ошибка `BRANCH_NOT_FOUND`, если хотя бы один не найден.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byIds(ids: List<BranchId>): List<Branch>
}
