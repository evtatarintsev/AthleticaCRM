package org.athletica.crm.usecases.memberships

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.domain.memberships.Membership
import org.athletica.crm.domain.tasks.Tasks
import org.athletica.crm.i18n.Messages
import org.athletica.crm.read.memberships.UnpaidSessionsView
import org.athletica.crm.storage.Transaction

/**
 * После активации абонемента [membership] без участия сотрудника проверяет неоплаченные занятия
 * клиента в группах абонемента и, если они есть, создаёт задачу «Проверить клиента» — с привязкой
 * к клиенту, без исполнителя, с названием абонемента и числом долгов в описании. Вызывается
 * один раз на активацию, в её транзакции: при активации списанием — после списания, поэтому
 * активировавшее занятие в счёт не попадает.
 */
context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun createCheckClientTaskIfDebts(unpaidSessions: UnpaidSessionsView, tasks: Tasks, membership: Membership) {
    val count = unpaidSessions.byMembership(membership.id).sessions.size
    if (count == 0) {
        return
    }
    tasks.new(
        id = TaskId.new(),
        title = Messages.CheckClientTaskTitle.localize(),
        description = Messages.CheckClientTaskDescription.localize(membership.name, count),
        clientId = membership.clientId,
        dueDate = null,
        dueDateEnd = null,
    )
}
