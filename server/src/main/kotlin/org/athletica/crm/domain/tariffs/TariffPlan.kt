package org.athletica.crm.domain.tariffs

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.TariffPlanId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.DurationUnit
import org.athletica.crm.storage.Transaction

/**
 * Тарифный план абонемента — шаблон параметров для выдачи.
 */
interface TariffPlan {
    /** Идентификатор плана. */
    val id: TariffPlanId

    /** Отображаемое название. */
    val name: String

    /** Количество занятий; `null` — безлимит. */
    val sessions: Int?

    /** Числовое значение срока действия. */
    val durationValue: Int

    /** Единица измерения срока действия. */
    val durationUnit: DurationUnit

    /** Стоимость плана. */
    val price: Money

    /** Признак архивного плана. */
    val archived: Boolean

    /**
     * Сохраняет параметры тарифа: INSERT при создании либо UPDATE существующего.
     * Признак архивности не затрагивается — для этого есть [archive] и [unarchive].
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun save()

    /** Архивирует тариф: он исчезает из активного списка, но остаётся для ранее выданных абонементов. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun archive()

    /** Восстанавливает тариф из архива. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun unarchive()

    /** Возвращает копию с новыми параметрами (без записи в БД); архивность сохраняется. */
    fun withNew(
        name: String,
        sessions: Int?,
        durationValue: Int,
        durationUnit: DurationUnit,
        price: Money,
    ): TariffPlan
}
