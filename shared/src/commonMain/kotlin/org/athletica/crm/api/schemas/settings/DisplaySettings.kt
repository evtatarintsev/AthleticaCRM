package org.athletica.crm.api.schemas.settings

import kotlinx.serialization.Serializable

@Serializable
data class DisplaySettings(
    val clients: ClientsDisplaySettings = ClientsDisplaySettings(),
)

@Serializable
data class ClientsDisplaySettings(
    val columns: List<String> = listOf("gender", "birth_year", "debt"),
)
