package org.athletica.crm.domain.tariffs

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.Row
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.TariffPlanId
import org.athletica.crm.core.entityids.toTariffPlanId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Money
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
        tr.sql(
            """
            SELECT id, name, sessions_count, duration_value, duration_unit, price, is_archived
            FROM subscription_tariffs
            WHERE org_id = :orgId AND (is_archived = false OR :includeArchived)
            ORDER BY is_archived, name
            """.trimIndent(),
        )
            .bind("orgId", ctx.orgId)
            .bind("includeArchived", includeArchived)
            .list { it.toTariffPlan() }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(
        id: TariffPlanId,
        name: String,
        sessions: Int?,
        durationValue: Int,
        durationUnit: DurationUnit,
        price: Money,
    ): TariffPlan = DbTariffPlan(id, name, sessions, durationValue, durationUnit, price, archived = false)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: TariffPlanId): TariffPlan =
        byIds(listOf(id)).singleOrNull()
            ?: raise(CommonDomainError("TARIFF_NOT_FOUND", "Тарифный план не найден"))

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byIds(ids: List<TariffPlanId>): List<TariffPlan> {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) {
            return emptyList()
        }

        val result =
            tr.sql(
                """
                SELECT id, name, sessions_count, duration_value, duration_unit, price, is_archived
                FROM subscription_tariffs
                WHERE id = ANY(:ids) AND org_id = :orgId
                """.trimIndent(),
            )
                .bind("ids", distinctIds)
                .bind("orgId", ctx.orgId)
                .list { it.toTariffPlan() }

        if (result.size != distinctIds.size) {
            raise(CommonDomainError("TARIFF_NOT_FOUND", "Тарифный план не найден"))
        }
        return result
    }

    /** Собирает доменный тариф из строки результата. */
    context(ctx: EmployeeRequestContext)
    private fun Row.toTariffPlan(): TariffPlan =
        DbTariffPlan(
            id = asUuid("id").toTariffPlanId(),
            name = asString("name"),
            sessions = get("sessions_count", Int::class.javaObjectType),
            durationValue = get("duration_value", Int::class.javaObjectType)!!,
            durationUnit = DurationUnit.valueOf(asString("duration_unit")),
            price = asMoney("price", ctx.currency),
            archived = asBoolean("is_archived"),
        )
}
