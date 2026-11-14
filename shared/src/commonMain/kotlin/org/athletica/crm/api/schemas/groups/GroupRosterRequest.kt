package org.athletica.crm.api.schemas.groups

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.EndingSoonReason

/** Запрос состава группы для её карточки. */
@Serializable
data class GroupRosterRequest(
    /** Группа. */
    val groupId: GroupId,
)

/** Состав группы с абонементом, долгом, балансом и причиной внимания. */
@Serializable
data class GroupRosterResponse(
    /** Активные участники: сначала по причине внимания (`DEBT`, `NO_MEMBERSHIP`, `MEMBERSHIP_ENDING`, без причины), внутри — по имени. */
    val clients: List<GroupRosterClient>,
)

/** Участник в составе группы. */
@Serializable
data class GroupRosterClient(
    /** Идентификатор клиента. */
    val id: ClientId,
    /** Имя клиента. */
    val name: String,
    /** Аватар клиента; `null` — не загружен. */
    val avatarId: UploadId?,
    /** Абонемент, действующий в группе; `null` — такого нет. */
    val membership: GroupRosterMembership?,
    /** У клиента есть неактивированный абонемент этой группы помимо [membership]. */
    val hasNextMembership: Boolean,
    /** Число неоплаченных занятий клиента в этой группе. */
    val unpaidSessions: Int,
    /** Баланс личного счёта клиента. */
    val balance: Money,
    /** Причина внимания; `null` — участник в порядке. */
    val attention: GroupClientAttention?,
)

/** Абонемент, действующий в группе, в составе группы. */
@Serializable
data class GroupRosterMembership(
    /** Идентификатор абонемента. */
    val id: MembershipId,
    /** Название абонемента. */
    val name: String,
    /** Абонемент активирован (сохранённое состояние «Активный»); `false` — «Неактивирован». */
    val activated: Boolean,
    /** Общее количество занятий; `null` — безлимит. */
    val sessionsTotal: Int?,
    /** Остаток занятий; `null` — безлимит. */
    val sessionsRemaining: Int?,
    /** Дата окончания; `null` — у неактивированного ещё не определена. */
    val endDate: LocalDate?,
    /** Причины признака «заканчивается» на сегодня; пусто — не заканчивается или не активирован. */
    val endingSoon: List<EndingSoonReason>,
    /** Абонемент «к закрытию» на сегодня: срок вышел или занятия закончились. */
    val closePending: Boolean,
)

/** Причина, по которой участник группы требует внимания. */
@Serializable
enum class GroupClientAttention {
    /** Есть неоплаченные занятия в группе или отрицательный баланс. */
    DEBT,

    /** Нет абонемента, действующего в группе, или он «к закрытию». */
    NO_MEMBERSHIP,

    /** Абонемент заканчивается, следующий не куплен. */
    MEMBERSHIP_ENDING,
}
