package org.athletica.crm.api.schemas.sessions

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.SessionId

/** Запрос детальной информации о занятии по [id]. */
@Serializable
data class SessionDetailRequest(
    val id: SessionId,
)
