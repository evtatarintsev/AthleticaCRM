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

    /** Создаёт тарифный план [plan]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun create(plan: TariffPlan)

    /** Изменяет существующий тарифный план [plan]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun update(plan: TariffPlan)

    /** Архивирует ([archived] = true) или восстанавливает тариф [id]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun setArchived(id: TariffPlanId, archived: Boolean)
}

/**
 * Тарифный план абонемента — шаблон параметров для выдачи.
 */
data class TariffPlan(
    /** Идентификатор плана. */
    val id: TariffPlanId,
    /** Отображаемое название. */
    val name: String,
    /** Количество занятий; `null` — безлимит. */
    val sessions: Int?,
    /** Числовое значение срока действия. */
    val durationValue: Int,
    /** Единица измерения срока действия. */
    val durationUnit: DurationUnit,
    /** Стоимость плана. */
    val price: Money,
    /** Признак архивного плана. */
    val archived: Boolean,
)
