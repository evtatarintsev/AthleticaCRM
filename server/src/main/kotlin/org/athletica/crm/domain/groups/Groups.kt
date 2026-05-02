package org.athletica.crm.domain.groups

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

interface Groups {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun new(
        id: GroupId,
        name: String,
        schedule: List<ScheduleSlot>,
        disciplineIds: List<DisciplineId>,
        employeeIds: List<EmployeeId>,
    ): Group

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(): List<Group>

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byId(id: GroupId): Group
}
