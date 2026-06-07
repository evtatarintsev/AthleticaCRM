package org.athletica.crm.api.schemas.memberships

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ClientId

/**
 * Запрос списка абонементов клиента.
 */
@Serializable
data class MembershipListRequest(
    /** Клиент, чьи абонементы запрашиваются. */
    val clientId: ClientId,
)

/**
 * Ответ со списком абонементов клиента.
 */
@Serializable
data class MembershipListResponse(
    /** Абонементы клиента, новые сверху. */
    val memberships: List<MembershipSchema>,
)
