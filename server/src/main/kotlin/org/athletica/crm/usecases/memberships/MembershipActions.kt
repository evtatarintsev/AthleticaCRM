package org.athletica.crm.usecases.memberships

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.domain.memberships.ActiveMembership
import org.athletica.crm.domain.memberships.ClosedMembership
import org.athletica.crm.domain.memberships.Memberships
import org.athletica.crm.domain.memberships.RevokedMembership
import org.athletica.crm.domain.memberships.closeManually
import org.athletica.crm.domain.memberships.extendManually
import org.athletica.crm.domain.memberships.freezeManually
import org.athletica.crm.domain.memberships.revokeManually
import org.athletica.crm.domain.memberships.unfreezeManually
import org.athletica.crm.domain.org.OrgCalendar
import org.athletica.crm.storage.Transaction

/**
 * Закрывает абонемент [id] вручную сегодняшней датой организации из [calendar].
 * Закрыть можно только активный абонемент; иначе — ошибка, абонемент не меняется.
 */
context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
suspend fun closeMembership(memberships: Memberships, calendar: OrgCalendar, id: MembershipId): ClosedMembership = memberships.byId(id).closeManually(calendar.today())

/**
 * Отзывает абонемент [id]. Отозвать можно неактивированный или активный абонемент;
 * закрытый и отозванный — финальные, для них ошибка.
 */
context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
suspend fun revokeMembership(memberships: Memberships, id: MembershipId): RevokedMembership = memberships.byId(id).revokeManually()

/**
 * Замораживает активный абонемент [id] на период [start]..[end] по причине [reason].
 * Сохранённая, но закончившаяся заморозка сначала снимается.
 */
context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
suspend fun freezeMembership(
    memberships: Memberships,
    calendar: OrgCalendar,
    id: MembershipId,
    start: LocalDate,
    end: LocalDate,
    reason: String,
): ActiveMembership = memberships.byId(id).freezeManually(start, end, reason, calendar.today())

/**
 * Продлевает активный абонемент [id] до [endDate] по причине [reason].
 * Сохранённая, но закончившаяся заморозка сначала снимается; действующая или запланированная —
 * ошибка, абонемент не меняется.
 */
context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
suspend fun extendMembership(
    memberships: Memberships,
    calendar: OrgCalendar,
    id: MembershipId,
    endDate: LocalDate,
    reason: String,
): ActiveMembership = memberships.byId(id).extendManually(endDate, reason, calendar.today())

/**
 * Снимает заморозку абонемента [id] сегодняшней датой организации:
 * запланированная отменяется целиком, действующая размораживается досрочно.
 */
context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
suspend fun unfreezeMembership(
    memberships: Memberships,
    calendar: OrgCalendar,
    id: MembershipId,
): ActiveMembership = memberships.byId(id).unfreezeManually(calendar.today())
