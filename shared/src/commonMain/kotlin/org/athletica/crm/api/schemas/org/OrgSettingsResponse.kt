package org.athletica.crm.api.schemas.org

import kotlinx.serialization.Serializable
import org.athletica.crm.core.money.Currency

@Serializable
data class OrgSettingsResponse(
    val name: String,
    val timezone: String,
    /**
     * Валюта организации. Задаётся при регистрации и не редактируется через настройки;
     * присылается только для отображения.
     */
    val currency: Currency,
)
