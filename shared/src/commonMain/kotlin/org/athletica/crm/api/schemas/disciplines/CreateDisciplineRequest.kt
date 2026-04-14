package org.athletica.crm.api.schemas.disciplines

import kotlinx.serialization.Serializable
import org.athletica.crm.core.DisciplineId

@Serializable
data class CreateDisciplineRequest(
    val id: DisciplineId,
    val name: String,
)
