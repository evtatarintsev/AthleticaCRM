package org.athletica.crm.domain.groups

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.employees.Employee
import org.athletica.crm.storage.Transaction

interface Groups {
    /**
     * Создаёт группу в текущем филиале сотрудника.
     * Требует контекста сотрудника, т.к. брать филиал из JWT-токена.
     * [employees] — преподаватели группы; каждый должен иметь доступ к филиалу [EmployeeRequestContext.branchId],
     * иначе ошибка `EMPLOYEE_NOT_FOUND`.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun new(
        id: GroupId,
        name: String,
        schedule: List<ScheduleSlot>,
        disciplineIds: List<DisciplineId>,
        employees: List<Employee>,
    ): Group

    /**
     * Возвращает список групп организации.
     * Фильтрует по [RequestContext.branchIdOrNull]: если задан — только для этого филиала,
     * если null (системный контекст) — все группы организации.
     * Опциональные фильтры:
     * [nameQuery] — поиск по подстроке в названии (case-insensitive); null/пустая строка — без фильтра.
     * [disciplineIds] — оставить только группы, у которых есть хотя бы одна из указанных дисциплин;
     * пустой список — без фильтра.
     * [employeeIds] — оставить только группы, у которых есть хотя бы один из указанных тренеров;
     * пустой список — без фильтра.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(
        nameQuery: String? = null,
        disciplineIds: List<DisciplineId> = emptyList(),
        employeeIds: List<EmployeeId> = emptyList(),
    ): List<Group>

    /**
     * Возвращает общее количество групп организации (без учёта фильтров [list]),
     * с учётом ограничения по филиалу из [RequestContext.branchIdOrNull].
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun totalCount(): Int

    /** Возвращает группу по идентификатору. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byId(id: GroupId): Group
}
