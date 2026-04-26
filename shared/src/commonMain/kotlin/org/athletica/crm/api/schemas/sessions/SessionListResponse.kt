package org.athletica.crm.api.schemas.sessions

import kotlinx.serialization.Serializable

/** Ответ на запрос списка занятий за период. */
@Serializable
data class SessionListResponse(
    val sessions: List<SessionListItem>,
)
