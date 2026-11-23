package org.athletica.crm.routes

import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.settings.DisplaySettings
import org.athletica.crm.domain.settings.UserDisplaySettings
import org.athletica.crm.storage.Database

context(db: Database)
fun RouteWithContext.displaySettingsRoutes(userSettings: UserDisplaySettings) {
    get<Unit, DisplaySettings>("/display-settings") {
        db.transaction {
            userSettings.get().toDisplaySettings()
        }
    }
    post<DisplaySettings, DisplaySettings>("/display-settings/update") { request ->
        db.transaction {
            userSettings.save(Json.encodeToString(request))
            userSettings.get().toDisplaySettings()
        }
    }
}

/** Схема настроек из сохранённого JSON-документа; без документа — настройки по умолчанию. */
private fun String?.toDisplaySettings(): DisplaySettings = this?.let { Json.decodeFromString<DisplaySettings>(it) } ?: DisplaySettings()
