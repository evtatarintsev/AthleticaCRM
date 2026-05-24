package org.athletica.crm.domain.groups

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

interface Groups {
    /**
     * Создаёт группу в текущем филиале сотрудника.
     * Требует контекста сотрудника, т.к. брать филиал из JWT-токена.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun new(
        id: GroupId,
        name: String,
        schedule: List<ScheduleSlot>,
        disciplineIds: List<DisciplineId>,
        employeeIds: List<EmployeeId>,
    ): Group

    /**
     * Возвращает список групп организации.
     * Фильтрует по [RequestContext.branchIdOrNull]: если задан — только для этого филиала,
     * если null (системный контекст) — все группы организации.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(): List<Group>

    /** Возвращает группу по идентификатору. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byId(id: GroupId): Group
}
