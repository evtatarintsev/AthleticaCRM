package org.athletica.crm.domain.tariffs

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.TariffPlanId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.DurationUnit
import org.athletica.crm.storage.Transaction

/**
 * R2DBC-реализация тарифного плана. Все запросы фильтруются по `org_id`.
 * Валюта цены берётся из контекста организации, отдельной колонки нет.
 */
data class DbTariffPlan(
    override val id: TariffPlanId,
    override val name: String,
    override val sessions: Int?,
    override val durationValue: Int,
    override val durationUnit: DurationUnit,
    override val price: Money,
    override val archived: Boolean,
) : TariffPlan {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun save() {
        tr.sql(
            """
            INSERT INTO subscription_tariffs
                (id, org_id, name, sessions_count, duration_value, duration_unit, price)
            VALUES (:id, :orgId, :name, :sessions, :durationValue, :durationUnit, :price)
            ON CONFLICT (id) DO UPDATE SET
                name           = EXCLUDED.name,
                sessions_count = EXCLUDED.sessions_count,
                duration_value = EXCLUDED.duration_value,
                duration_unit  = EXCLUDED.duration_unit,
                price          = EXCLUDED.price
            WHERE subscription_tariffs.org_id = :orgId
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .bind("name", name)
            .bind("sessions", sessions)
            .bind("durationValue", durationValue)
            .bind("durationUnit", durationUnit.name)
            .bind("price", price)
            .execute()
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun archive() = setArchived(true)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun unarchive() = setArchived(false)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    private suspend fun setArchived(value: Boolean) {
        tr.sql("UPDATE subscription_tariffs SET is_archived = :archived WHERE id = :id AND org_id = :orgId")
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .bind("archived", value)
            .execute()
    }

    override fun withNew(
        name: String,
        sessions: Int?,
        durationValue: Int,
        durationUnit: DurationUnit,
        price: Money,
    ): TariffPlan =
        copy(
            name = name,
            sessions = sessions,
            durationValue = durationValue,
            durationUnit = durationUnit,
            price = price,
        )
}
