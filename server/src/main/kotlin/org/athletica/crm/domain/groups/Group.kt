package org.athletica.crm.domain.groups

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.employees.Employee
import org.athletica.crm.storage.Transaction

/**
 * Группа — постоянный состав занимающихся в одном филиале.
 *
 * Расписание группой не владеет: у слота есть период действия, поэтому «расписание группы»
 * без указания даты не существует. Читается и меняется расписание через [GroupSchedule].
 */
interface Group {
    val id: GroupId

    /** Идентификатор филиала, к которому относится группа. */
    val branchId: BranchId

    /** Название группы. */
    val name: String

    /** Дисциплины, привязанные к группе. */
    val disciplines: List<DisciplineId>

    /** Преподаватели группы, используемые как шаблон при генерации занятий. */
    val employeeIds: List<EmployeeId>

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun save()

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun withNewDisciplines(disciplines: List<DisciplineId>): Group

    /**
     * Заменяет преподавателей группы.
     * Каждый [Employee] из [employees] должен иметь доступ к филиалу [branchId];
     * иначе при последующем [save] поднимется ошибка `EMPLOYEE_NOT_FOUND`.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun withNewEmployees(employees: List<Employee>): Group

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun withNewName(name: String): Group

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun withNew(
        name: String,
        disciplines: List<DisciplineId>,
        employees: List<Employee>,
    ): Group =
        withNewName(name)
            .withNewDisciplines(disciplines)
            .withNewEmployees(employees)
}
