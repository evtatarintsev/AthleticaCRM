package org.athletica.crm.api.schemas.memberships

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.TariffPlanId
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.ActivationMode
import org.athletica.crm.core.subscription.CloseMode
import org.athletica.crm.core.subscription.DurationUnit

/**
 * Запрос на выдачу абонемента клиенту.
 *
 * Дата начала и окончания определяются на сервере режимом активации и сроком
 * ([durationValue]/[durationUnit]). Оплата и баланс на этом этапе не затрагиваются.
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
    /** Группы, в которых действует абонемент; хотя бы одна. */
    val groupIds: List<GroupId>,
    /** Числовое значение срока действия. */
    val durationValue: Int,
    /** Единица измерения срока действия. */
    val durationUnit: DurationUnit,
    /** Стоимость абонемента на момент выдачи (без учёта оплаты/баланса). */
    val price: Money,
    /** Режим активации. */
    val activationMode: ActivationMode,
    /** Дата активации для режима [ActivationMode.ON_DATE]; для других режимов не передаётся. */
    val activateOn: LocalDate? = null,
    /** Крайняя дата активации для режима [ActivationMode.ON_FIRST_VISIT]; `null` — без крайней даты. */
    val activationDeadline: LocalDate? = null,
    /** Режим закрытия. */
    val closeMode: CloseMode,
)
