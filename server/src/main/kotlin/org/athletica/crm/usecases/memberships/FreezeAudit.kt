package org.athletica.crm.usecases.memberships

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.subscription.UnfreezeReason
import org.athletica.crm.domain.audit.AuditActionType
import org.athletica.crm.domain.audit.AuditEvent
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.memberships.ActiveMembership
import org.athletica.crm.domain.memberships.MembershipFreeze
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction

/**
 * Пишет в системный аудит оформление ([unfreezeReason] `null`) или снятие заморозки [freeze]
 * абонемента [membershipId] с новой датой окончания [membershipEndDate]. Автор — сотрудник из
 * контекста, для системного контекста — «Система».
 */
context(ctx: RequestContext, tr: Transaction)
suspend fun AuditLog.logFreezeChange(
    membershipId: MembershipId,
    freeze: MembershipFreeze,
    unfreezeReason: UnfreezeReason?,
    membershipEndDate: LocalDate,
) {
    val employee = ctx as? EmployeeRequestContext
    log(
        AuditEvent(
            orgId = ctx.orgId,
            userId = employee?.userId,
            username = employee?.username ?: Messages.AuditSystemActor.localize(),
            actionType = AuditActionType.UPDATE,
            entityType = "membership",
            entityId = membershipId.value,
            data =
                Json.encodeToString(
                    FreezeAuditData(
                        action = if (unfreezeReason == null) "FROZEN" else "UNFROZEN",
                        unfreezeReason = unfreezeReason,
                        freezeStart = freeze.start,
                        freezeEnd = freeze.end,
                        freezeReason = freeze.reason,
                        membershipEndDate = membershipEndDate,
                    ),
                ),
            ipAddress = employee?.clientIp,
        ),
    )
}

/** Данные записи аудита о заморозке абонемента. */
@Serializable
private data class FreezeAuditData(
    /** `FROZEN` — оформление, `UNFROZEN` — снятие. */
    val action: String,
    /** Основание снятия; `null` для оформления. */
    val unfreezeReason: UnfreezeReason?,
    /** Первый день заморозки. */
    val freezeStart: LocalDate,
    /** Последний день заморозки. */
    val freezeEnd: LocalDate,
    /** Причина заморозки. */
    val freezeReason: String,
    /** Дата окончания абонемента после изменения. */
    val membershipEndDate: LocalDate,
)

/**
 * Снимает с абонемента [membership] закончившуюся к [today] заморозку, если она ещё сохранена,
 * и пишет снятие в аудит [audit]; иначе возвращает абонемент без изменений.
 */
context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun clearExpiredFreeze(
    audit: AuditLog,
    membership: ActiveMembership,
    today: LocalDate,
): ActiveMembership {
    val freeze = membership.freeze
    if (freeze == null || freeze.statusAt(today) != null) {
        return membership
    }
    return membership.unfreeze(today, UnfreezeReason.EXPIRED).also {
        audit.logFreezeChange(it.id, freeze, UnfreezeReason.EXPIRED, it.endDate)
    }
}
