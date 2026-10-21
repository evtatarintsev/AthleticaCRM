package org.athletica.crm.usecases.memberships

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.subscription.CloseReason
import org.athletica.crm.core.subscription.FreezeStatus
import org.athletica.crm.core.subscription.UnfreezeReason
import org.athletica.crm.domain.memberships.ActiveMembership
import org.athletica.crm.domain.memberships.ClosedMembership
import org.athletica.crm.domain.memberships.InactiveMembership
import org.athletica.crm.domain.memberships.Membership
import org.athletica.crm.domain.memberships.Memberships
import org.athletica.crm.domain.memberships.RevokedMembership
import org.athletica.crm.domain.org.OrgCalendar
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction

/**
 * Закрывает абонемент [id] вручную сегодняшней датой организации из [calendar].
 * Закрыть можно только активный абонемент; иначе — ошибка, абонемент не меняется.
 */
context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun closeMembership(memberships: Memberships, calendar: OrgCalendar, id: MembershipId): ClosedMembership =
    when (val membership = memberships.byId(id)) {
        is ActiveMembership -> membership.close(CloseReason.MANUAL, calendar.today())
        is InactiveMembership -> raise(CommonDomainError("MEMBERSHIP_NOT_ACTIVE", Messages.MembershipNotActive.localize()))
        is ClosedMembership, is RevokedMembership -> raise(finalMembershipError())
    }

/**
 * Отзывает абонемент [id]. Отозвать можно неактивированный или активный абонемент;
 * закрытый и отозванный — финальные, для них ошибка.
 */
context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun revokeMembership(memberships: Memberships, id: MembershipId): RevokedMembership =
    when (val membership: Membership = memberships.byId(id)) {
        is InactiveMembership -> membership.revoke()
        is ActiveMembership -> membership.revoke()
        is ClosedMembership, is RevokedMembership -> raise(finalMembershipError())
    }

/**
 * Замораживает активный абонемент [id] на период [start]..[end] по причине [reason].
 * Сохранённая, но закончившаяся заморозка сначала снимается.
 */
context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun freezeMembership(
    memberships: Memberships,
    calendar: OrgCalendar,
    id: MembershipId,
    start: LocalDate,
    end: LocalDate,
    reason: String,
): ActiveMembership =
    when (val membership = memberships.byId(id)) {
        is ActiveMembership -> {
            val today = calendar.today()
            clearExpiredFreeze(membership, today).freeze(start, end, reason, today)
        }
        is InactiveMembership -> raise(CommonDomainError("MEMBERSHIP_NOT_ACTIVE", Messages.MembershipNotActive.localize()))
        is ClosedMembership, is RevokedMembership -> raise(finalMembershipError())
    }

/**
 * Продлевает активный абонемент [id] до [endDate] по причине [reason].
 * Сохранённая, но закончившаяся заморозка сначала снимается; действующая или запланированная —
 * ошибка, абонемент не меняется.
 */
context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun extendMembership(
    memberships: Memberships,
    calendar: OrgCalendar,
    id: MembershipId,
    endDate: LocalDate,
    reason: String,
): ActiveMembership =
    when (val membership = memberships.byId(id)) {
        is ActiveMembership -> {
            val today = calendar.today()
            clearExpiredFreeze(membership, today).extend(endDate, reason, today)
        }
        is InactiveMembership -> raise(CommonDomainError("MEMBERSHIP_NOT_ACTIVE", Messages.MembershipNotActive.localize()))
        is ClosedMembership, is RevokedMembership -> raise(finalMembershipError())
    }

/**
 * Снимает заморозку абонемента [id] сегодняшней датой организации:
 * запланированная отменяется целиком, действующая размораживается досрочно.
 */
context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun unfreezeMembership(
    memberships: Memberships,
    calendar: OrgCalendar,
    id: MembershipId,
): ActiveMembership =
    when (val membership = memberships.byId(id)) {
        is ActiveMembership -> {
            val today = calendar.today()
            val freeze = membership.freezeAt(today) ?: raise(CommonDomainError("MEMBERSHIP_NOT_FROZEN", Messages.MembershipNotFrozen.localize()))
            val reason = if (freeze.statusAt(today) == FreezeStatus.SCHEDULED) UnfreezeReason.CANCELLED else UnfreezeReason.MANUAL
            membership.unfreeze(today, reason)
        }
        is InactiveMembership -> raise(CommonDomainError("MEMBERSHIP_NOT_ACTIVE", Messages.MembershipNotActive.localize()))
        is ClosedMembership, is RevokedMembership -> raise(finalMembershipError())
    }

/**
 * Снимает с абонемента [membership] закончившуюся к [today] заморозку, если она ещё сохранена;
 * иначе возвращает абонемент без изменений.
 */
context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun clearExpiredFreeze(membership: ActiveMembership, today: LocalDate): ActiveMembership {
    val freeze = membership.freeze
    if (freeze == null || freeze.statusAt(today) != null) {
        return membership
    }
    return membership.unfreeze(today, UnfreezeReason.EXPIRED)
}

/** Ошибка действия над абонементом в финальном состоянии. */
context(ctx: RequestContext)
private fun finalMembershipError() = CommonDomainError("MEMBERSHIP_FINAL", Messages.MembershipFinal.localize())
