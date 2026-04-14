package org.athletica.crm.api.schemas.disciplines

import kotlinx.serialization.Serializable
import org.athletica.crm.core.DisciplineId

@Serializable
data class UpdateDisciplineRequest(
    val id: DisciplineId,
    val name: String,
)
