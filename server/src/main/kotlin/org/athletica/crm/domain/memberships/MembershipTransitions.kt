package org.athletica.crm.domain.memberships

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.subscription.ActivationReason
import org.athletica.crm.core.subscription.CloseMode
import org.athletica.crm.core.subscription.CloseReason
import org.athletica.crm.core.subscription.FreezeStatus
import org.athletica.crm.core.subscription.UnfreezeReason
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction

context(ctx: RequestContext, raise: Raise<AppError>)
private fun Membership.activeForTransition(): ActiveMembership =
    when (this) {
        is ActiveMembership -> this
        is InactiveMembership -> raise(CommonDomainError("MEMBERSHIP_NOT_ACTIVE", Messages.MembershipNotActive.localize()))
        is ClosedMembership, is RevokedMembership -> raise(finalMembershipError())
    }

context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
suspend fun Membership.closeManually(today: LocalDate): ClosedMembership = activeForTransition().close(CloseReason.MANUAL, today)

context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
suspend fun Membership.revokeManually(): RevokedMembership =
    when (this) {
        is InactiveMembership -> revoke()
        is ActiveMembership -> revoke()
        is ClosedMembership, is RevokedMembership -> raise(finalMembershipError())
    }

context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
suspend fun Membership.freezeManually(
    start: LocalDate,
    end: LocalDate,
    reason: String,
    today: LocalDate,
): ActiveMembership = activeForTransition().clearExpiredFreeze(today).freeze(start, end, reason, today)

context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
suspend fun Membership.extendManually(end: LocalDate, reason: String, today: LocalDate): ActiveMembership = activeForTransition().clearExpiredFreeze(today).extend(end, reason, today)

context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
suspend fun Membership.unfreezeManually(today: LocalDate): ActiveMembership {
    val active = activeForTransition()
    val status =
        active.freeze?.statusAt(today)
            ?: raise(CommonDomainError("MEMBERSHIP_NOT_FROZEN", Messages.MembershipNotFrozen.localize()))
    val reason = if (status == FreezeStatus.SCHEDULED) UnfreezeReason.CANCELLED else UnfreezeReason.MANUAL
    return active.unfreeze(today, reason)
}

context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
private suspend fun ActiveMembership.clearExpiredFreeze(today: LocalDate): ActiveMembership {
    val current = freeze ?: return this
    return if (today > current.end) unfreeze(today, UnfreezeReason.EXPIRED) else this
}

context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
suspend fun Membership.advanceCalendar(today: LocalDate): Membership =
    when (this) {
        is InactiveMembership.OnDate ->
            if (activation.date <= today) activate(activation.date, ActivationReason.ON_DATE) else this
        is InactiveMembership.OnFirstVisit -> {
            val deadline = activation.deadline
            if (deadline != null && deadline <= today) activate(deadline, ActivationReason.ON_DEADLINE) else this
        }
        is ActiveMembership -> {
            val active = clearExpiredFreeze(today)
            if (active.closeMode == CloseMode.ON_EXPIRY && active.endDate < today) active.close(CloseReason.EXPIRY, today) else active
        }
        is ClosedMembership, is RevokedMembership -> this
    }

context(ctx: RequestContext)
private fun finalMembershipError() = CommonDomainError("MEMBERSHIP_FINAL", Messages.MembershipFinal.localize())
