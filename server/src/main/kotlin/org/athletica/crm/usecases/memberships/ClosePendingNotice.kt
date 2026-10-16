package org.athletica.crm.usecases.memberships

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.employees.Employees
import org.athletica.crm.domain.memberships.ActiveMembership
import org.athletica.crm.domain.notifications.MembershipClosePending
import org.athletica.crm.domain.notifications.Notifications
import org.athletica.crm.storage.Transaction

/**
 * Отправляет выдавшему уведомление «к закрытию» по абонементу [membership], если на дату [today]
 * оно положено ([ActiveMembership.closeNotificationDueAt]), и отмечает отправку в абонементе и
 * его истории — один раз на эпизод. Если выдавший удалён или неизвестен, уведомление не отправляется
 * и отправка не отмечается. Возвращает абонемент после возможной отметки.
 */
context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun notifyClosePendingIfDue(
    notifications: Notifications,
    employees: Employees,
    membership: ActiveMembership,
    today: LocalDate,
): ActiveMembership {
    val issuedBy = membership.issuedBy
    if (issuedBy == null || !membership.closeNotificationDueAt(today)) {
        return membership
    }
    if (employees.byIds(listOf(issuedBy)).none { it.isActive }) {
        return membership
    }
    notifications
        .new(MembershipClosePending(membership.clientId, membership.id, membership.name), listOf(issuedBy))
        .save()
    return membership.markCloseNotified()
}
