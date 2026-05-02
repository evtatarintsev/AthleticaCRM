package org.athletica.crm.navigation

import androidx.navigation.NavBackStackEntry
import androidx.navigation.toRoute

object AppNavUrlEncoder {
    fun entryToUrl(entry: NavBackStackEntry): String {
        val route = entry.destination.route ?: return "/"
        return when {
            "AppRoute.ClientDetail" in route -> "clients/${runCatching { entry.toRoute<AppRoute.ClientDetail>().id }.getOrElse { "" }}"
            "AppRoute.ClientCreate" in route -> "clients/new"
            "AppRoute.ClientEdit" in route -> "clients/${runCatching { entry.toRoute<AppRoute.ClientEdit>().id }.getOrElse { "" }}/edit"
            "AppRoute.Clients" in route -> "clients"
            "AppRoute.GroupCreate" in route -> "groups/new"
            "AppRoute.Groups" in route -> "groups"
            "AppRoute.EmployeeDetail" in route -> "employees/${runCatching { entry.toRoute<AppRoute.EmployeeDetail>().id }.getOrElse { "" }}"
            "AppRoute.EmployeeCreate" in route -> "employees/new"
            "AppRoute.EmployeeEdit" in route -> "employees/${runCatching { entry.toRoute<AppRoute.EmployeeEdit>().id }.getOrElse { "" }}/edit"
            "AppRoute.Employees" in route -> "employees"
            "AppRoute.Schedule" in route -> "schedule"
            "AppRoute.SettingsBasic" in route -> "settings/basic"
            "AppRoute.SettingsClientSources" in route -> "settings/client-sources"
            "AppRoute.SettingsClientAdditionalAttributes" in route -> "settings/client-additional-attributes"
            "AppRoute.SettingsHalls" in route -> "settings/halls"
            "AppRoute.SettingsDisciplines" in route -> "settings/disciplines"
            "AppRoute.SettingsRoles" in route -> "settings/roles"
            "AppRoute.SettingsActivityLog" in route -> "settings/activity-log"
            "AppRoute.SettingsChangePassword" in route -> "settings/change-password"
            "AppRoute.SettingsEditProfile" in route -> "settings/edit-profile"
            "AppRoute.Settings" in route -> "settings"
            else -> ""
        }
    }

    fun decodeUrl(url: String): AppRoute {
        val path = url.removePrefix("#").removePrefix("/").trimEnd('/')
        val segments = if (path.isEmpty()) emptyList() else path.split("/").filter { it.isNotEmpty() }
        return when {
            segments.isEmpty() -> AppRoute.Home
            segments == listOf("clients") -> AppRoute.Clients
            segments == listOf("clients", "new") -> AppRoute.ClientCreate
            segments.size == 2 && segments[0] == "clients" -> AppRoute.ClientDetail(segments[1])
            segments.size == 3 && segments[0] == "clients" && segments[2] == "edit" -> AppRoute.ClientEdit(segments[1])
            segments == listOf("groups") -> AppRoute.Groups
            segments == listOf("groups", "new") -> AppRoute.GroupCreate
            segments == listOf("employees") -> AppRoute.Employees
            segments == listOf("employees", "new") -> AppRoute.EmployeeCreate
            segments.size == 2 && segments[0] == "employees" -> AppRoute.EmployeeDetail(segments[1])
            segments.size == 3 && segments[0] == "employees" && segments[2] == "edit" -> AppRoute.EmployeeEdit(segments[1])
            segments == listOf("schedule") -> AppRoute.Schedule
            segments == listOf("settings") -> AppRoute.Settings
            segments == listOf("settings", "basic") -> AppRoute.SettingsBasic
            segments == listOf("settings", "client-sources") -> AppRoute.SettingsClientSources
            segments == listOf("settings", "client-additional-attributes") -> AppRoute.SettingsClientAdditionalAttributes
            segments == listOf("settings", "halls") -> AppRoute.SettingsHalls
            segments == listOf("settings", "disciplines") -> AppRoute.SettingsDisciplines
            segments == listOf("settings", "roles") -> AppRoute.SettingsRoles
            segments == listOf("settings", "activity-log") -> AppRoute.SettingsActivityLog
            segments == listOf("settings", "change-password") -> AppRoute.SettingsChangePassword
            segments == listOf("settings", "edit-profile") -> AppRoute.SettingsEditProfile
            else -> AppRoute.Home
        }
    }
}
