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
import org.athletica.crm.domain.audit.logUpdate
import org.athletica.crm.storage.Transaction

/** Декоратор [TariffPlans], оборачивающий выдаваемые тарифы в [AuditTariffPlan]. */
class AuditTariffPlans(private val delegate: TariffPlans, private val audit: AuditLog) : TariffPlans by delegate {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(includeArchived: Boolean) = delegate.list(includeArchived).map { AuditTariffPlan(it, audit) }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(
        id: TariffPlanId,
        name: String,
        sessions: Int?,
        durationValue: Int,
        durationUnit: DurationUnit,
        price: Money,
    ) = AuditTariffPlan(delegate.new(id, name, sessions, durationValue, durationUnit, price), audit)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: TariffPlanId) = AuditTariffPlan(delegate.byId(id), audit)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byIds(ids: List<TariffPlanId>) = delegate.byIds(ids).map { AuditTariffPlan(it, audit) }
}

/** Декоратор [TariffPlan], логирующий сохранение и архивирование. */
class AuditTariffPlan(private val delegate: TariffPlan, private val audit: AuditLog) : TariffPlan by delegate {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun save() =
        delegate.save().also {
            audit.logUpdate("subscription_tariff", id, Json.encodeToString(toAuditData()))
        }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun archive() =
        delegate.archive().also {
            audit.logUpdate("subscription_tariff", id, Json.encodeToString(ArchiveAuditData(archived = true)))
        }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun unarchive() =
        delegate.unarchive().also {
            audit.logUpdate("subscription_tariff", id, Json.encodeToString(ArchiveAuditData(archived = false)))
        }

    override fun withNew(
        name: String,
        sessions: Int?,
        durationValue: Int,
        durationUnit: DurationUnit,
        price: Money,
    ) = AuditTariffPlan(delegate.withNew(name, sessions, durationValue, durationUnit, price), audit)

    private fun toAuditData() = TariffPlanAuditData(name, sessions, durationValue, durationUnit, price, archived)
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
