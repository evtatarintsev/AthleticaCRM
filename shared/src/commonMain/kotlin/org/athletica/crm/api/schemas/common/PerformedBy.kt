package org.athletica.crm.api.schemas.common

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** DTO сотрудника, выполнившего операцию: id и имя для отображения. */
@Serializable
data class PerformedBy(
    val id: Uuid,
    val name: String,
)
