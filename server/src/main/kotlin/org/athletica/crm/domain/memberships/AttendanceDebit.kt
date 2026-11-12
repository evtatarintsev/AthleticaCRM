package org.athletica.crm.domain.memberships

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.subscription.ActivationReason
import org.athletica.crm.core.subscription.UnfreezeReason
import org.athletica.crm.domain.attendance.Participation
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction

internal fun Membership.matchesParticipation(participation: Participation): Boolean = participation.shouldBeDebited && participation.clientId == clientId && groups.any { it.id == participation.groupId }

context(ctx: RequestContext)
internal fun sessionAlreadyDebited() = CommonDomainError("ALREADY_DEBITED", Messages.SessionAlreadyDebited.localize())

internal data class AppliedDebit(val membership: Membership, val activated: Boolean)

/** Приводит списание в соответствие с уже заблокированным участием. */
context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
internal suspend fun Memberships.reconcileDebit(
    sessionId: SessionId,
    clientId: ClientId,
    participation: Participation?,
    today: LocalDate,
): AppliedDebit? {
    val existing = activeDebit(sessionId, clientId)
    if (participation?.shouldBeDebited != true) {
        if (existing != null) {
            when (val membership = byId(existing.membershipId)) {
                is ActiveMembership -> membership.reverse(existing, today)
                is ClosedMembership -> membership.reverse(existing)
                is InactiveMembership, is RevokedMembership ->
                    error("Действующее списание ${existing.id} на абонементе ${membership.id} без списаний")
            }
        }
        return null
    }
    if (existing != null) return null
    val groupId = checkNotNull(participation.groupId)
    val membership = debitCandidate(clientId, groupId, participation.sessionDate, today) ?: return null
    return AppliedDebit(membership.applyAttendanceDebit(participation, today), membership is InactiveMembership)
}

context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
internal suspend fun Membership.applyAttendanceDebit(participation: Participation, today: LocalDate): Membership {
    val groupId = participation.groupId
    val day = participation.sessionDate
    if (groupId == null || !matchesParticipation(participation) || !isDebitCandidate(groupId, day, today)) {
        raise(CommonDomainError("SESSION_NOT_UNPAID", Messages.SessionNotUnpaid.localize()))
    }
    val active =
        when (this) {
            is ActiveMembership -> if (freezeAt(today)?.covers(day) == true) unfreeze(day, UnfreezeReason.VISIT) else this
            is InactiveMembership.OnDate -> activate(activation.date, ActivationReason.ON_DATE)
            is InactiveMembership.OnFirstVisit -> activate(day, ActivationReason.FIRST_VISIT)
            is ClosedMembership, is RevokedMembership -> error("Подобран абонемент $id в финальном состоянии")
        }
    return active.debit(participation, today)
}
