package org.athletica.crm.usecases.groups

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.employees.Employees
import org.athletica.crm.domain.groups.GroupSchedule
import org.athletica.crm.domain.groups.Groups
import org.athletica.crm.domain.groups.NewSlot
import org.athletica.crm.domain.notifications.GroupScheduleChanged
import org.athletica.crm.domain.notifications.Notifications
import org.athletica.crm.domain.sessions.ScheduleSync
import org.athletica.crm.storage.Transaction

/**
 * Устанавливает расписание группы [groupId] из [slots] начиная с [effectiveFrom]
 * (`null` — с сегодняшнего дня), приводит занятия в соответствие с ним
 * и уведомляет об изменении остальных активных сотрудников, которым доступен филиал группы.
 */
context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun setGroupSchedule(
    schedule: GroupSchedule,
    sync: ScheduleSync,
    groups: Groups,
    employees: Employees,
    notifications: Notifications,
    groupId: GroupId,
    effectiveFrom: LocalDate?,
    slots: List<NewSlot>,
) {
    val from = effectiveFrom ?: java.time.LocalDate.now().toKotlinLocalDate()
    schedule.setFrom(groupId, from, slots)
    sync.sync()

    val group = groups.byId(groupId)
    notifications
        .new(
            GroupScheduleChanged(
                groupId = group.id,
                groupName = group.name,
                effectiveFrom = from,
                changedByName = employees.byId(ctx.employeeId).name,
            ),
            employees.activeIdsWithAccessTo(group.branchId) - ctx.employeeId,
        ).save()
}
