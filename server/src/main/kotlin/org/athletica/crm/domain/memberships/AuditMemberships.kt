package org.athletica.crm.domain.memberships

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.TariffPlanId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.ActivationMode
import org.athletica.crm.core.subscription.CloseMode
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logUpdate
import org.athletica.crm.storage.Transaction

/** Декоратор [Memberships], логирующий в журнал аудита выдачу абонемента сотрудником. */
class AuditMemberships(private val delegate: Memberships, private val audit: AuditLog) : Memberships by delegate {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun issue(
        id: MembershipId,
        clientId: ClientId,
        tariffPlanId: TariffPlanId?,
        name: String,
        sessions: Int?,
        duration: MembershipDuration,
        price: Money,
        activation: Activation,
        closeMode: CloseMode,
        issuedOn: LocalDate,
    ): Membership =
        delegate.issue(id, clientId, tariffPlanId, name, sessions, duration, price, activation, closeMode, issuedOn).also {
            if (ctx is EmployeeRequestContext) {
                context(ctx) {
                    audit.logUpdate("membership", id, Json.encodeToString(MembershipAuditData(clientId, tariffPlanId, name, sessions, activation.mode, closeMode, issuedOn, price)))
                }
            }
        }
}

/** Снимок выдачи абонемента для журнала аудита (доменная сущность не сериализуется напрямую). */
@Serializable
private data class MembershipAuditData(
    val clientId: ClientId,
    val tariffPlanId: TariffPlanId?,
    val name: String,
    val sessionsTotal: Int?,
    val activationMode: ActivationMode,
    val closeMode: CloseMode,
    val issuedOn: LocalDate,
    val price: Money,
)
