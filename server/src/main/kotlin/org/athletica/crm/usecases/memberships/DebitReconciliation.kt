package org.athletica.crm.usecases.memberships

import arrow.core.getOrElse
import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.SystemContexts
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.subscription.ActivationReason
import org.athletica.crm.core.subscription.UnfreezeReason
import org.athletica.crm.domain.attendance.Participation
import org.athletica.crm.domain.attendance.Participations
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.employees.Employees
import org.athletica.crm.domain.events.AttendanceChanged
import org.athletica.crm.domain.events.AttendanceMarked
import org.athletica.crm.domain.events.SessionCompleted
import org.athletica.crm.domain.memberships.ActiveMembership
import org.athletica.crm.domain.memberships.ClosedMembership
import org.athletica.crm.domain.memberships.InactiveMembership
import org.athletica.crm.domain.memberships.MembershipDebit
import org.athletica.crm.domain.memberships.Memberships
import org.athletica.crm.domain.memberships.RevokedMembership
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
    private val audit: AuditLog,
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
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    private suspend fun reconcileParticipation(sessionId: SessionId, clientId: ClientId) {
        val participation = participations.lock(sessionId, clientId)
        val existing = memberships.activeDebit(sessionId, clientId)
        val today = calendar.today()
        val groupId = participation?.groupId
        when {
            participation?.shouldBeDebited == true && groupId != null && existing == null ->
                debit(participation, groupId, today)
            participation?.shouldBeDebited != true && existing != null -> reverse(existing, today)
            else -> Unit
        }
    }

    /**
     * Списывает занятие [participation] группы [groupId] с подобранного абонемента, активируя
     * неактивированный: «с даты» — датой активации, «при первом посещении» — датой занятия.
     * Если дата занятия попадает в период текущей заморозки, заморозка сначала снимается с возвратом
     * неиспользованных дней и записью в аудит.
     * Если абонемент стал «к закрытию», сразу уведомляет выдавшего. Если списание активировало
     * абонемент, в той же транзакции проверяет долги клиента и при их наличии создаёт задачу
     * «Проверить клиента»: активация → списание → проверка долгов → задача.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    private suspend fun debit(participation: Participation, groupId: GroupId, today: LocalDate) {
        val candidate =
            memberships.debitCandidate(participation.clientId, groupId, participation.sessionDate, today) ?: return
        val activated = candidate is InactiveMembership
        val active =
            when (candidate) {
                is ActiveMembership -> unfreezeForVisit(candidate, participation.sessionDate, today)
                is InactiveMembership.OnDate -> candidate.activate(candidate.activation.date, ActivationReason.ON_DATE)
                is InactiveMembership.OnFirstVisit -> candidate.activate(participation.sessionDate, ActivationReason.FIRST_VISIT)
                is ClosedMembership, is RevokedMembership -> error("Подобран абонемент ${candidate.id} в финальном состоянии")
            }
        val debited = active.debit(participation.sessionId, participation.sessionDate, today)
        if (debited is ActiveMembership) {
            notifyClosePendingIfDue(notifications, employees, debited, today)
        }
        if (activated) {
            createCheckClientTaskIfDebts(unpaidSessions, tasks, debited)
        }
    }

    /**
     * Снимает с абонемента [membership] заморозку, в период которой попадает посещение от [sessionDate]
     * при сегодняшней дате [today]; иначе возвращает абонемент без изменений.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    private suspend fun unfreezeForVisit(membership: ActiveMembership, sessionDate: LocalDate, today: LocalDate): ActiveMembership {
        val freeze = membership.freezeAt(today)
        if (freeze == null || !freeze.covers(sessionDate)) {
            return membership
        }
        return membership.unfreeze(sessionDate, UnfreezeReason.VISIT).also {
            audit.logFreezeChange(it.id, freeze, UnfreezeReason.VISIT, it.endDate)
        }
    }

    /** Возвращает занятие списания [debit] на его абонемент — активный или закрытый. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    private suspend fun reverse(debit: MembershipDebit, today: LocalDate) {
        when (val membership = memberships.byId(debit.membershipId)) {
            is ActiveMembership -> membership.reverse(debit, today)
            is ClosedMembership -> membership.reverse(debit)
            is InactiveMembership, is RevokedMembership ->
                error("Действующее списание ${debit.id} на абонементе ${membership.id} без списаний")
        }
    }
}
