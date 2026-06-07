package org.athletica.crm.domain.memberships

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.TariffPlanId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.DurationUnit
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logUpdate
import org.athletica.crm.storage.Transaction

/** Декоратор [Memberships], оборачивающий выдаваемые абонементы в [AuditMembership]. */
class AuditMemberships(private val delegate: Memberships, private val audit: AuditLog) : Memberships by delegate {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(
        id: MembershipId,
        clientId: ClientId,
        tariffPlanId: TariffPlanId?,
        name: String,
        sessions: Int?,
        durationValue: Int,
        durationUnit: DurationUnit,
        startDate: LocalDate,
        price: Money,
    ) = AuditMembership(
        delegate.new(id, clientId, tariffPlanId, name, sessions, durationValue, durationUnit, startDate, price),
        audit,
    )

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun forClient(clientId: ClientId) = delegate.forClient(clientId).map { AuditMembership(it, audit) }
}

/** Декоратор [Membership], логирующий выдачу абонемента. */
class AuditMembership(private val delegate: Membership, private val audit: AuditLog) : Membership by delegate {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun save() =
        delegate.save().also {
            audit.logUpdate("membership", id, Json.encodeToString(toAuditData()))
        }

    private fun toAuditData() = MembershipAuditData(clientId, tariffPlanId, name, sessionsTotal, startDate, endDate, price)
}

/** Снимок абонемента для журнала аудита (доменная сущность не сериализуется напрямую). */
@Serializable
private data class MembershipAuditData(
    val clientId: ClientId,
    val tariffPlanId: TariffPlanId?,
    val name: String,
    val sessionsTotal: Int?,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val price: Money,
)
