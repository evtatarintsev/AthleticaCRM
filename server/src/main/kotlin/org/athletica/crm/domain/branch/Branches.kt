package org.athletica.crm.domain.branch

import arrow.core.raise.context.Raise
import kotlinx.serialization.Serializable
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

interface Branches {
    /**
     * Возвращает филиалы, доступные текущему сотруднику.
     * Если у сотрудника [org.athletica.crm.domain.employees.EmployeeBranchAccess.All] — все филиалы организации;
     * иначе — только явно назначенные через `employee_branches`.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction)
    suspend fun list(): List<Branch>

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun create(branch: Branch)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun update(branch: Branch)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun delete(ids: List<BranchId>)
}

@Serializable
data class Branch(
    val id: BranchId,
    val name: String,
)
