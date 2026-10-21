package org.athletica.crm.api.schemas.memberships

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.MembershipId

/** Запрос на закрытие активного абонемента сотрудником. */
@Serializable
data class CloseMembershipRequest(
    /** Закрываемый абонемент. */
    val membershipId: MembershipId,
)

/** Запрос на отзыв неактивированного или активного абонемента. */
@Serializable
data class RevokeMembershipRequest(
    /** Отзываемый абонемент. */
    val membershipId: MembershipId,
)

/** Запрос на заморозку активного абонемента на период [startDate]..[endDate] включительно. */
@Serializable
data class FreezeMembershipRequest(
    /** Замораживаемый абонемент. */
    val membershipId: MembershipId,
    /** Первый день заморозки, не раньше сегодняшнего. */
    val startDate: LocalDate,
    /** Последний день заморозки включительно. */
    val endDate: LocalDate,
    /** Причина заморозки. */
    val reason: String,
)

/** Запрос на ручное продление активного незамороженного абонемента до [endDate]. */
@Serializable
data class ExtendMembershipRequest(
    /** Продлеваемый абонемент. */
    val membershipId: MembershipId,
    /** Новая дата окончания, позже текущей. */
    val endDate: LocalDate,
    /** Причина продления. */
    val reason: String,
)

/** Запрос на снятие заморозки: запланированная отменяется, действующая снимается досрочно. */
@Serializable
data class UnfreezeMembershipRequest(
    /** Абонемент с заморозкой. */
    val membershipId: MembershipId,
)
