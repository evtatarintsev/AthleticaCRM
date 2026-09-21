package org.athletica.crm.domain.groups

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.employees.Employee
import org.athletica.crm.storage.Transaction

interface Groups {
    /**
     * Создаёт группу в текущем филиале сотрудника. Расписание задаётся отдельно —
     * через [GroupSchedule.setFrom] в той же транзакции.
     * Требует контекста сотрудника, т.к. брать филиал из JWT-токена.
     * [employees] — преподаватели группы; каждый должен иметь доступ к филиалу [EmployeeRequestContext.branchId],
     * иначе ошибка `EMPLOYEE_NOT_FOUND`.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun new(
        id: GroupId,
        name: String,
        disciplineIds: List<DisciplineId>,
        employees: List<Employee>,
    ): Group

    /**
     * Возвращает группы организации для операций записи (генерация занятий, обработчики событий).
     * Фильтрует по [RequestContext.branchIdOrNull]: если задан — только для этого филиала,
     * если null (системный контекст) — все группы организации.
     *
     * Выборка под нужды экранов — в [org.athletica.crm.read.groups.GroupListView].
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(): List<Group>

    /** Возвращает группу по идентификатору. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byId(id: GroupId): Group
}
