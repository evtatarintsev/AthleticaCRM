package org.athletica.crm.usecases.memberships

import arrow.core.getOrElse
import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.SystemContexts
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.domain.attendance.Participations
import org.athletica.crm.domain.employees.Employees
import org.athletica.crm.domain.events.AttendanceChanged
import org.athletica.crm.domain.events.AttendanceMarked
import org.athletica.crm.domain.events.SessionCompleted
import org.athletica.crm.domain.memberships.ActiveMembership
import org.athletica.crm.domain.memberships.Memberships
import org.athletica.crm.domain.memberships.reconcileDebit
import org.athletica.crm.domain.notifications.Notifications
import org.athletica.crm.domain.org.OrgCalendar
import org.athletica.crm.domain.tasks.Tasks
import org.athletica.crm.read.memberships.UnpaidSessionsView
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.Transaction

/**
 * Сверка списаний абонементов с журналом посещаемости — обработчик `SessionCompleted`,
 * `AttendanceChanged` и `AttendanceMarked`.
 *
 * Для каждого участия решение принимается по текущему состоянию БД, а не по событию:
 * «должно быть списание» — занятие проведено и отметка «пришёл». Должно, а действующего нет —
 * подбирается абонемент и занятие списывается; не должно, а есть — занятие возвращается.
 * Поэтому повторная доставка и порядок событий одного участия не меняют итога.
 *
 * «Нет подходящего абонемента» — нормальный исход: занятие остаётся неоплаченным. Исключение
 * (в том числе `MembershipDebitConflict`) означает временный сбой: транзакция откатывается,
 * воркер событий повторяет обработку.
 */
class DebitReconciliation(
    private val db: Database,
    private val memberships: Memberships,
    private val participations: Participations,
    private val notifications: Notifications,
    private val employees: Employees,
    private val calendar: OrgCalendar,
    private val unpaidSessions: UnpaidSessionsView,
    private val tasks: Tasks,
) {
    /** Сверяет списания всех участников проведённого занятия [event] организации [orgId]. */
    suspend fun sessionCompleted(orgId: OrgId, event: SessionCompleted) {
        event.participants.forEach { reconcile(orgId, event.sessionId, it.clientId) }
    }

    /** Сверяет списание участия, отметка которого изменена событием [event]. */
    suspend fun attendanceChanged(orgId: OrgId, event: AttendanceChanged) {
        reconcile(orgId, event.sessionId, event.clientId)
    }

    /** Сверяет списание участия, получившего отметку событием [event]. */
    suspend fun attendanceMarked(orgId: OrgId, event: AttendanceMarked) {
        reconcile(orgId, event.sessionId, event.clientId)
    }

    /**
     * Сверяет списание за участие клиента [clientId] в занятии [sessionId] организации [orgId]
     * в отдельной транзакции системного контекста. Доменная ошибка здесь — непредвиденная
     * и откатывает транзакцию, как исключение.
     */
    suspend fun reconcile(orgId: OrgId, sessionId: SessionId, clientId: ClientId) {
        db.transaction {
            either {
                context(SystemContexts.forOrg(orgId)) {
                    reconcileParticipation(sessionId, clientId)
                }
            }.getOrElse { throw IllegalStateException("Сверка списания $sessionId/$clientId не выполнена: $it") }
        }
    }

    /** Приводит списание за участие в соответствие с журналом. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    private suspend fun reconcileParticipation(sessionId: SessionId, clientId: ClientId) {
        val participation = participations.lock(sessionId, clientId)
        val today = calendar.today()
        val debit = memberships.reconcileDebit(sessionId, clientId, participation, today) ?: return
        val membership = debit.membership
        if (membership is ActiveMembership) {
            notifyClosePendingIfDue(notifications, employees, membership, today)
        }
        if (debit.activated) {
            createCheckClientTaskIfDebts(unpaidSessions, tasks, membership)
        }
    }
}
