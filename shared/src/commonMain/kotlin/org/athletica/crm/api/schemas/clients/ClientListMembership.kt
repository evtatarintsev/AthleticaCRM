package org.athletica.crm.api.schemas.clients

import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.subscription.ActivationMode
import org.athletica.crm.core.subscription.EndingSoonReason
import org.athletica.crm.core.subscription.TermPosition

/** Абонемент клиента в строке списка клиентов: активный или неактивированный. */
@Serializable
data class ClientListMembership(
    /** Идентификатор абонемента. */
    val id: MembershipId,
    /** Название абонемента. */
    val name: String,
    /** Общее количество занятий; `null` — безлимит. */
    val sessionsTotal: Int?,
    /** Остаток занятий; `null` — безлимит. */
    val sessionsRemaining: Int?,
    /** Сохранённое состояние и выводимые на сегодня признаки. */
    val state: ClientListMembershipState,
)

/** Состояние абонемента в строке списка клиентов (дискриминатор `type`). */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface ClientListMembershipState

/** Неактивированный абонемент. */
@Serializable
@SerialName("inactive")
data class InactiveClientListMembershipState(
    /** Режим активации, заданный при выдаче. */
    val activationMode: ActivationMode,
    /** Дата активации для режима «с даты»; `null` для остальных режимов. */
    val activateOn: LocalDate?,
) : ClientListMembershipState

/** Активный абонемент с признаками на сегодня в часовом поясе организации. */
@Serializable
@SerialName("active")
data class ActiveClientListMembershipState(
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
) : ClientListMembershipState
