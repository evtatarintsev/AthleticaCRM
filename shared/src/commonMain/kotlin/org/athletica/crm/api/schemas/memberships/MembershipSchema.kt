package org.athletica.crm.api.schemas.memberships

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.MembershipStatus

/**
 * Выданный абонемент в ответах API.
 */
@Serializable
data class MembershipSchema(
    /** Идентификатор абонемента. */
    val id: MembershipId,
    /** Название абонемента. */
    val name: String,
    /** Общее количество занятий; `null` — безлимит. */
    val sessionsTotal: Int?,
    /** Остаток занятий; `null` — безлимит. */
    val sessionsRemaining: Int?,
    /** Дата начала действия. */
    val startDate: LocalDate,
    /** Дата окончания действия. */
    val endDate: LocalDate,
    /** Стоимость абонемента на момент выдачи. */
    val price: Money,
    /** Статус абонемента (действует/истёк). */
    val status: MembershipStatus,
)
