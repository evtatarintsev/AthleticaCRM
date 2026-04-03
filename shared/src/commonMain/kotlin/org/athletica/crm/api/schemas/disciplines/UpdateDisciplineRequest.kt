package org.athletica.crm.api.schemas.disciplines

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class UpdateDisciplineRequest(
    val id: Uuid,
    val name: String,
)
