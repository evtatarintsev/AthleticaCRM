package org.athletica.crm.routes

import org.athletica.crm.api.schemas.settings.DisplaySettings
import org.athletica.crm.domain.settings.UserDisplaySettings
import org.athletica.crm.storage.Database

context(db: Database)
fun RouteWithContext.displaySettingsRoutes(userSettings: UserDisplaySettings) {
    get<Unit, DisplaySettings>("/display-settings") {
        db.transaction {
            userSettings.get()
        }
    }
    post<DisplaySettings, DisplaySettings>("/display-settings/update") { request ->
        db.transaction {
            userSettings.save(request)
            userSettings.get()
        }
    }
}
