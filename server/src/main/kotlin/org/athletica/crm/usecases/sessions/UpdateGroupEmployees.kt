package org.athletica.crm.usecases.sessions

import arrow.core.raise.context.Raise
import kotlinx.datetime.toKotlinLocalDate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.groups.Groups
import org.athletica.crm.domain.sessions.Sessions
import org.athletica.crm.storage.Transaction

/**
 * Обновляет преподавателей группы и синхронизирует будущие занятия,
 * у которых состав преподавателей не переопределяли вручную.
 */
context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun updateGroupEmployees(
    groups: Groups,
    sessions: Sessions,
    groupId: GroupId,
    employeeIds: List<EmployeeId>,
) {
    groups.byId(groupId).withNewEmployees(employeeIds.distinct()).save()
    val today = java.time.LocalDate.now().toKotlinLocalDate()
    sessions.syncFutureEmployeesFromGroup(groupId, employeeIds.distinct(), today)
}
