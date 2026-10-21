package org.athletica.crm.domain.memberships

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.TariffPlanId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.ActivationMode
import org.athletica.crm.core.subscription.CloseMode
import org.athletica.crm.core.subscription.UnfreezeReason
import org.athletica.crm.domain.audit.AuditActionType
import org.athletica.crm.domain.audit.AuditEvent
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logUpdate
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction

/**
 * Декоратор [Memberships], логирующий в журнал аудита выдачу абонемента сотрудником, а также
 * оформление и снятие заморозки: активные абонементы, которые он возвращает, обёрнуты [AuditActiveMembership].
 */
class AuditMemberships(private val delegate: Memberships, private val audit: AuditLog) : Memberships by delegate {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun issue(
        id: MembershipId,
        clientId: ClientId,
        tariffPlanId: TariffPlanId?,
        name: String,
        sessions: Int?,
        groupIds: List<GroupId>,
        duration: MembershipDuration,
        price: Money,
        activation: Activation,
        closeMode: CloseMode,
        issuedOn: LocalDate,
    ): Membership =
        delegate.issue(id, clientId, tariffPlanId, name, sessions, groupIds, duration, price, activation, closeMode, issuedOn).also {
            if (ctx is EmployeeRequestContext) {
                context(ctx) {
                    audit.logUpdate("membership", id, Json.encodeToString(MembershipAuditData(clientId, tariffPlanId, name, sessions, groupIds, activation.mode, closeMode, issuedOn, price)))
                }
            }
        }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: MembershipId): Membership = audited(delegate.byId(id))

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun debitCandidate(
        clientId: ClientId,
        groupId: GroupId,
        sessionDate: LocalDate,
        today: LocalDate,
    ): Membership? = delegate.debitCandidate(clientId, groupId, sessionDate, today)?.let { audited(it) }

    /** Оборачивает активный абонемент [membership] аудитом заморозки; остальные состояния — без изменений. */
    private fun audited(membership: Membership): Membership =
        if (membership is ActiveMembership) {
            AuditActiveMembership(membership, audit)
        } else {
            membership
        }
}

/**
 * Декоратор [ActiveMembership], пишущий в журнал аудита оформление и снятие заморозки в той же
 * транзакции. Автор — сотрудник из контекста, для системного контекста — «Система».
 */
private class AuditActiveMembership(private val delegate: ActiveMembership, private val audit: AuditLog) : ActiveMembership by delegate {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun freeze(
        start: LocalDate,
        end: LocalDate,
        reason: String,
        today: LocalDate,
    ): ActiveMembership =
        AuditActiveMembership(delegate.freeze(start, end, reason, today), audit).also {
            logFreezeChange(requireNotNull(it.freeze), null, it.endDate)
        }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun unfreeze(day: LocalDate, reason: UnfreezeReason): ActiveMembership {
        val freeze = delegate.freeze
        return AuditActiveMembership(delegate.unfreeze(day, reason), audit).also {
            logFreezeChange(requireNotNull(freeze), reason, it.endDate)
        }
    }

    /**
     * Пишет в аудит оформление ([unfreezeReason] `null`) или снятие заморозки [freeze]
     * с новой датой окончания [membershipEndDate].
     */
    context(ctx: RequestContext, tr: Transaction)
    private suspend fun logFreezeChange(
        freeze: MembershipFreeze,
        unfreezeReason: UnfreezeReason?,
        membershipEndDate: LocalDate,
    ) {
        val employee = ctx as? EmployeeRequestContext
        audit.log(
            AuditEvent(
                orgId = ctx.orgId,
                userId = employee?.userId,
                username = employee?.username ?: Messages.AuditSystemActor.localize(),
                actionType = AuditActionType.UPDATE,
                entityType = "membership",
                entityId = id.value,
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

/** Снимок выдачи абонемента для журнала аудита (доменная сущность не сериализуется напрямую). */
@Serializable
private data class MembershipAuditData(
    val clientId: ClientId,
    val tariffPlanId: TariffPlanId?,
    val name: String,
    val sessionsTotal: Int?,
    val groupIds: List<GroupId>,
    val activationMode: ActivationMode,
    val closeMode: CloseMode,
    val issuedOn: LocalDate,
    val price: Money,
)
