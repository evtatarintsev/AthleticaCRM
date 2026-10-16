package org.athletica.crm.api.schemas.memberships

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
