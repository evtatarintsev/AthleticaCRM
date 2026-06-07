package org.athletica.crm.domain.tariffs

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.TariffPlanId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.DurationUnit
import org.athletica.crm.storage.Transaction

/**
 * Каталог тарифных планов абонементов организации.
 *
 * Тарифы мутабельны: правка влияет только на будущие выдачи абонементов, потому что
 * выдаваемый абонемент хранит собственный снимок параметров. Удаления нет —
 * только архивирование, так как на тариф ссылаются ранее выданные абонементы.
 */
interface TariffPlans {
    /** Возвращает тарифы организации; архивные включаются только при [includeArchived]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(includeArchived: Boolean): List<TariffPlan>

    /** Создаёт несохранённый тариф (не архивный); запись в БД — через [TariffPlan.save]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun new(
        id: TariffPlanId,
        name: String,
        sessions: Int?,
        durationValue: Int,
        durationUnit: DurationUnit,
        price: Money,
    ): TariffPlan

    /** Возвращает тариф по идентификатору; ошибка `TARIFF_NOT_FOUND`, если не найден. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byId(id: TariffPlanId): TariffPlan

    /**
     * Возвращает тарифы по идентификаторам.
     * Дубликаты в [ids] игнорируются; ошибка `TARIFF_NOT_FOUND`, если хотя бы один не найден.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byIds(ids: List<TariffPlanId>): List<TariffPlan>
}
