package org.athletica.crm.api.schemas.disciplines

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class DeleteDisciplineRequest(
    val ids: List<Uuid>,
)
