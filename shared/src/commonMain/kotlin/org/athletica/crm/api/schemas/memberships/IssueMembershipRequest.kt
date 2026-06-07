package org.athletica.crm.api.schemas.memberships

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.TariffPlanId
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.DurationUnit

/**
 * Запрос на выдачу абонемента клиенту.
 *
 * Дата окончания вычисляется на сервере из [startDate] и срока ([durationValue]/[durationUnit]).
 * Оплата и баланс на этом этапе не затрагиваются — фиксируется только сам абонемент.
 */
@Serializable
data class IssueMembershipRequest(
    /** Идентификатор абонемента (генерируется клиентом). */
    val id: MembershipId,
    /** Клиент, которому выдаётся абонемент. */
    val clientId: ClientId,
    /** Тариф-шаблон, на основе которого выдан абонемент; `null` — индивидуальный. */
    val tariffPlanId: TariffPlanId?,
    /** Название абонемента (снимок на момент выдачи). */
    val name: String,
    /** Количество занятий; `null` — безлимит. */
    val sessions: Int?,
    /** Числовое значение срока действия. */
    val durationValue: Int,
    /** Единица измерения срока действия. */
    val durationUnit: DurationUnit,
    /** Дата начала действия. */
    val startDate: LocalDate,
    /** Стоимость абонемента на момент выдачи (без учёта оплаты/баланса). */
    val price: Money,
)
