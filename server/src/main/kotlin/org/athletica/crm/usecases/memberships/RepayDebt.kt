package org.athletica.crm.usecases.memberships

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.attendance.Participations
import org.athletica.crm.domain.memberships.ActiveMembership
import org.athletica.crm.domain.memberships.DebtSession
import org.athletica.crm.domain.memberships.Membership
import org.athletica.crm.domain.memberships.Memberships
import org.athletica.crm.domain.org.OrgCalendar
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction

/**
 * Гасит долг клиента с активного абонемента [membershipId]: списывает выбранные неоплаченные
 * занятия [sessionIds]. Каждое участие сначала блокируется — так же, как в сверке списаний, —
 * и только потом проверяется: занятие проведено, отметка «пришёл», группа входит в группы
 * абонемента, действующего списания нет. Иначе — ошибка, ничего не списано.
 */
context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun repayDebt(
    memberships: Memberships,
    participations: Participations,
    calendar: OrgCalendar,
    membershipId: MembershipId,
    sessionIds: List<SessionId>,
): Membership {
    val ids = sessionIds.distinct()
    if (ids.isEmpty()) {
        raise(CommonDomainError("DEBT_SESSIONS_REQUIRED", Messages.DebtSessionsRequired.localize()))
    }
    val membership =
        memberships.byId(membershipId) as? ActiveMembership
            ?: raise(CommonDomainError("MEMBERSHIP_NOT_ACTIVE", Messages.MembershipNotActive.localize()))
    val groupIds = membership.groups.map { it.id }.toSet()
    val sessions =
        ids.map { sessionId ->
            val participation = participations.lock(sessionId, membership.clientId)
            if (participation == null || !participation.shouldBeDebited || participation.groupId !in groupIds) {
                raise(CommonDomainError("SESSION_NOT_UNPAID", Messages.SessionNotUnpaid.localize()))
            }
            if (memberships.activeDebit(sessionId, membership.clientId) != null) {
                raise(CommonDomainError("ALREADY_DEBITED", Messages.SessionAlreadyDebited.localize()))
            }
            DebtSession(sessionId, participation.sessionDate)
        }
    return membership.repayDebt(sessions, calendar.today())
}
