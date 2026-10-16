package org.athletica.crm.api.schemas.memberships

import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.TariffPlanId
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.ActivationMode
import org.athletica.crm.core.subscription.CloseMode
import org.athletica.crm.core.subscription.CloseReason
import org.athletica.crm.core.subscription.DurationUnit
import org.athletica.crm.core.subscription.EndingSoonReason
import org.athletica.crm.core.subscription.TermPosition

/**
 * Выданный абонемент в ответах API.
 */
@Serializable
data class MembershipSchema(
    /** Идентификатор абонемента. */
    val id: MembershipId,
    /** Название абонемента. */
    val name: String,
    /** Тариф-шаблон, на основе которого выдан абонемент; `null` — индивидуальный. */
    val tariffPlanId: TariffPlanId?,
    /** Общее количество занятий; `null` — безлимит. */
    val sessionsTotal: Int?,
    /** Остаток занятий; `null` — безлимит. */
    val sessionsRemaining: Int?,
    /** Числовое значение срока действия. */
    val durationValue: Int,
    /** Единица измерения срока действия. */
    val durationUnit: DurationUnit,
    /** Стоимость абонемента на момент выдачи. */
    val price: Money,
    /** Режим активации, заданный при выдаче. */
    val activationMode: ActivationMode,
    /** Крайняя дата активации для режима «при первом посещении»; `null` — не задана. */
    val activationDeadline: LocalDate?,
    /** Режим закрытия. */
    val closeMode: CloseMode,
    /** Дата выдачи в часовом поясе организации. */
    val issuedOn: LocalDate,
    /** Группы, в которых действует абонемент. */
    val groups: List<MembershipGroupSchema>,
    /** Сохранённое состояние и выводимые на сегодня свойства. */
    val state: MembershipStateSchema,
)

/** Группа абонемента. */
@Serializable
data class MembershipGroupSchema(
    /** Идентификатор группы. */
    val id: GroupId,
    /** Название группы. */
    val name: String,
)

/** Состояние абонемента (дискриминатор `type`). */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface MembershipStateSchema

/** Неактивирован; [activateOn] — дата активации для режима «с даты». */
@Serializable
@SerialName("inactive")
data class InactiveMembershipStateSchema(
    /** Дата активации для режима «с даты»; `null` для «при первом посещении». */
    val activateOn: LocalDate?,
) : MembershipStateSchema

/** Активный абонемент со свойствами, выведенными на сегодня в часовом поясе организации. */
@Serializable
@SerialName("active")
data class ActiveMembershipStateSchema(
    /** Дата начала действия. */
    val startDate: LocalDate,
    /** Дата окончания действия, последний день включительно. */
    val endDate: LocalDate,
    /** Положение сегодняшней даты относительно срока. */
    val termPosition: TermPosition,
    /** Причины признака «заканчивается»; пусто — не заканчивается. */
    val endingSoon: List<EndingSoonReason>,
    /** Признак «к закрытию»: срок вышел или занятия закончились. */
    val closePending: Boolean,
) : MembershipStateSchema

/** Закрытый абонемент. */
@Serializable
@SerialName("closed")
data class ClosedMembershipStateSchema(
    /** Дата начала действия. */
    val startDate: LocalDate,
    /** Дата окончания действия. */
    val endDate: LocalDate,
    /** Дата закрытия. */
    val closedOn: LocalDate,
    /** Причина закрытия. */
    val reason: CloseReason,
) : MembershipStateSchema

/** Отозванный абонемент. */
@Serializable
@SerialName("revoked")
data class RevokedMembershipStateSchema(
    /** Дата начала, если абонемент был активирован до отзыва. */
    val startDate: LocalDate?,
    /** Дата окончания, если абонемент был активирован до отзыва. */
    val endDate: LocalDate?,
) : MembershipStateSchema
