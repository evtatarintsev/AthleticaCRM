package org.athletica.crm.api.schemas.memberships

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.SessionId

/** Запрос неоплаченных занятий клиента в группах абонемента. */
@Serializable
data class UnpaidSessionsRequest(
    /** Абонемент: его клиент и группы задают выборку. */
    val membershipId: MembershipId,
)

/** Неоплаченные занятия клиента в группах абонемента, от старых к новым. */
@Serializable
data class UnpaidSessionsResponse(
    /** Неоплаченные занятия. */
    val sessions: List<UnpaidSessionSchema>,
)

/** Неоплаченное занятие: проведено, отметка «пришёл», действующего списания нет. */
@Serializable
data class UnpaidSessionSchema(
    /** Занятие. */
    val sessionId: SessionId,
    /** Дата занятия. */
    val date: LocalDate,
    /** Время начала. */
    val startTime: LocalTime,
    /** Группа занятия. */
    val groupId: GroupId,
    /** Название группы. */
    val groupName: String,
)

/** Запрос на погашение долга: списать выбранные неоплаченные занятия с активного абонемента. */
@Serializable
data class RepayDebtRequest(
    /** Абонемент, с которого гасится долг. */
    val membershipId: MembershipId,
    /** Неоплаченные занятия клиента в группах абонемента; не больше остатка. */
    val sessionIds: List<SessionId>,
)
