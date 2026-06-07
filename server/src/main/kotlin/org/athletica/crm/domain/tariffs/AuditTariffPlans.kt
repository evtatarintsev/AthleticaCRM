package org.athletica.crm.domain.tariffs

import arrow.core.raise.context.Raise
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.TariffPlanId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.DurationUnit
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logCreate
import org.athletica.crm.domain.audit.logUpdate
import org.athletica.crm.storage.Transaction

/**
 * Декоратор [TariffPlans], логирующий мутирующие операции (create/update/archive).
 * Чтение ([list]) не логируется.
 */
class AuditTariffPlans(private val delegate: TariffPlans, private val audit: AuditLog) : TariffPlans by delegate {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun create(plan: TariffPlan) =
        delegate.create(plan).also {
            audit.logCreate("subscription_tariff", plan.id, Json.encodeToString(plan.toAuditData()))
        }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun update(plan: TariffPlan) =
        delegate.update(plan).also {
            audit.logUpdate("subscription_tariff", plan.id, Json.encodeToString(plan.toAuditData()))
        }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun setArchived(id: TariffPlanId, archived: Boolean) =
        delegate.setArchived(id, archived).also {
            audit.logUpdate("subscription_tariff", id, Json.encodeToString(ArchiveAuditData(archived)))
        }
}

/** Снимок тарифа для журнала аудита (доменная сущность не сериализуется напрямую). */
@Serializable
private data class TariffPlanAuditData(
    val name: String,
    val sessions: Int?,
    val durationValue: Int,
    val durationUnit: DurationUnit,
    val price: Money,
    val archived: Boolean,
)

/** Данные операции архивирования для журнала аудита. */
@Serializable
private data class ArchiveAuditData(val archived: Boolean)

private fun TariffPlan.toAuditData() = TariffPlanAuditData(name, sessions, durationValue, durationUnit, price, archived)
