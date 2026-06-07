package org.athletica.crm.domain.tariffs

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.TariffPlanId
import org.athletica.crm.core.entityids.toTariffPlanId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.subscription.DurationUnit
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asMoney
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/**
 * R2DBC-реализация каталога тарифных планов. Все запросы фильтруются по `org_id`.
 * Валюта цены берётся из контекста организации, отдельной колонки нет.
 */
class DbTariffPlans : TariffPlans {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(includeArchived: Boolean): List<TariffPlan> =
        tr
            .sql(
                """
                SELECT id, name, sessions_count, duration_value, duration_unit, price, is_archived
                FROM subscription_tariffs
                WHERE org_id = :orgId AND (is_archived = false OR :includeArchived)
                ORDER BY is_archived, name
                """.trimIndent(),
            )
            .bind("orgId", ctx.orgId)
            .bind("includeArchived", includeArchived)
            .list { row ->
                TariffPlan(
                    id = row.asUuid("id").toTariffPlanId(),
                    name = row.asString("name"),
                    sessions = row.get("sessions_count", Int::class.javaObjectType),
                    durationValue = row.get("duration_value", Int::class.javaObjectType)!!,
                    durationUnit = DurationUnit.valueOf(row.asString("duration_unit")),
                    price = row.asMoney("price", ctx.currency),
                    archived = row.asBoolean("is_archived"),
                )
            }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun create(plan: TariffPlan) {
        tr
            .sql(
                """
                INSERT INTO subscription_tariffs
                    (id, org_id, name, sessions_count, duration_value, duration_unit, price)
                VALUES (:id, :orgId, :name, :sessions, :durationValue, :durationUnit, :price)
                """.trimIndent(),
            )
            .bind("id", plan.id)
            .bind("orgId", ctx.orgId)
            .bind("name", plan.name)
            .bind("sessions", plan.sessions)
            .bind("durationValue", plan.durationValue)
            .bind("durationUnit", plan.durationUnit.name)
            .bind("price", plan.price)
            .execute()
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun update(plan: TariffPlan) {
        val updatedRows =
            tr
                .sql(
                    """
                    UPDATE subscription_tariffs
                    SET name = :name,
                        sessions_count = :sessions,
                        duration_value = :durationValue,
                        duration_unit = :durationUnit,
                        price = :price
                    WHERE id = :id AND org_id = :orgId
                    """.trimIndent(),
                )
                .bind("id", plan.id)
                .bind("orgId", ctx.orgId)
                .bind("name", plan.name)
                .bind("sessions", plan.sessions)
                .bind("durationValue", plan.durationValue)
                .bind("durationUnit", plan.durationUnit.name)
                .bind("price", plan.price)
                .execute()

        if (updatedRows == 0L) {
            raise(CommonDomainError("TARIFF_NOT_FOUND", "Тарифный план не найден"))
        }
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun setArchived(id: TariffPlanId, archived: Boolean) {
        val updatedRows =
            tr
                .sql("UPDATE subscription_tariffs SET is_archived = :archived WHERE id = :id AND org_id = :orgId")
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .bind("archived", archived)
                .execute()

        if (updatedRows == 0L) {
            raise(CommonDomainError("TARIFF_NOT_FOUND", "Тарифный план не найден"))
        }
    }
}
