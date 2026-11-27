package org.athletica.crm.api.schemas.clientapp

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.subscription.EndingSoonReason

/** Состояние абонемента в кабинете: текущие — неактивирован и активен, история — закрыт и отозван. */
@Serializable
enum class ClientAppMembershipState { INACTIVE, ACTIVE, CLOSED, REVOKED }

/** Заморозка абонемента с [startDate] по [endDate] включительно; причины нет — это заметка сотрудника. */
@Serializable
data class ClientAppMembershipFreezeSchema(
    val startDate: LocalDate,
    val endDate: LocalDate,
)

/** Действующее списание занятия [sessionId] от [sessionDate] группы [groupName] (`null` — группа удалена). */
@Serializable
data class ClientAppMembershipDebitSchema(
    val sessionId: SessionId,
    val sessionDate: LocalDate,
    val groupName: String?,
)

/** Абонемент клиента в кабинете. */
@Serializable
data class ClientAppMembershipSchema(
    /** Идентификатор абонемента. */
    val id: MembershipId,
    /** Название абонемента. */
    val name: String,
    /** Состояние. */
    val state: ClientAppMembershipState,
    /** Всего занятий; `null` — безлимит. */
    val sessionsTotal: Int?,
    /** Остаток занятий; `null` — безлимит. */
    val sessionsRemaining: Int?,
    /** Дата начала; `null` — абонемент не активирован. */
    val startDate: LocalDate?,
    /** Последний день действия, уже сдвинутый на заморозку; `null` — абонемент не активирован. */
    val endDate: LocalDate?,
    /** Дата активации неактивированного абонемента «с даты»; иначе `null`. */
    val activateOn: LocalDate?,
    /** Причины признака «заканчивается» на сегодня организации; пусто — не заканчивается. */
    val endingSoon: List<EndingSoonReason>,
    /**
     * Активный абонемент закончился на сегодня организации: срок вышел или занятия исчерпаны, но сотрудник
     * его ещё не закрыл. Для клиента он уже не текущий.
     */
    val ended: Boolean,
    /** Запланированная или действующая заморозка; закончившаяся не отдаётся; `null` — нет. */
    val freeze: ClientAppMembershipFreezeSchema?,
    /** Дата закрытия закрытого абонемента; иначе `null`. */
    val closedOn: LocalDate?,
    /** Действующие списания от новых к старым. */
    val debits: List<ClientAppMembershipDebitSchema>,
)

/** Абонементы клиента: сначала активные, затем неактивированные, затем история от новых к старым. */
@Serializable
data class ClientAppMembershipsResponse(
    val memberships: List<ClientAppMembershipSchema>,
)
