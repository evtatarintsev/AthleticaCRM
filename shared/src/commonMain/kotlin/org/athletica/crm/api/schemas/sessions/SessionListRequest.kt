package org.athletica.crm.api.schemas.sessions

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.GroupId

/** Параметры запроса списка занятий за период с опциональной фильтрацией по группе. */
@Serializable
data class SessionListRequest(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val groupId: GroupId? = null,
)
