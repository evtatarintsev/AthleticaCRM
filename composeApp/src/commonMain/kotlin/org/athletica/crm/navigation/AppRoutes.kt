package org.athletica.crm.navigation

import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import kotlinx.serialization.Serializable
import org.athletica.crm.NavItem

@Serializable
sealed class AppRoute {
    @Serializable data object Home : AppRoute()

    @Serializable data object Schedule : AppRoute()

    @Serializable data object Clients : AppRoute()

    @Serializable data class ClientDetail(val id: String) : AppRoute()

    @Serializable data object ClientCreate : AppRoute()

    @Serializable data class ClientExport(val selectedIds: List<String> = emptyList()) : AppRoute()

    @Serializable data class ClientEdit(val id: String) : AppRoute()

    @Serializable data object Groups : AppRoute()

    @Serializable data class GroupDetail(val id: String) : AppRoute()

    @Serializable data object GroupCreate : AppRoute()

    @Serializable data object Employees : AppRoute()

    @Serializable data class EmployeeDetail(val id: String) : AppRoute()

    @Serializable data object EmployeeCreate : AppRoute()

    @Serializable data class EmployeeEdit(val id: String) : AppRoute()

    @Serializable data object Settings : AppRoute()

    @Serializable data object SettingsBasic : AppRoute()

    @Serializable data object SettingsClientSources : AppRoute()

    @Serializable data object SettingsClientAdditionalAttributes : AppRoute()

    @Serializable data object SettingsBranches : AppRoute()

    @Serializable data object SettingsHalls : AppRoute()

    @Serializable data object SettingsDisciplines : AppRoute()

    @Serializable data object SettingsRoles : AppRoute()

    @Serializable data object SettingsActivityLog : AppRoute()

    @Serializable data object SettingsChangePassword : AppRoute()

    @Serializable data object SettingsEditProfile : AppRoute()
}

fun NavItem.toRoute(): AppRoute =
    when (this) {
        NavItem.HOME -> AppRoute.Home
        NavItem.CLIENTS -> AppRoute.Clients
        NavItem.GROUPS -> AppRoute.Groups
        NavItem.SCHEDULE -> AppRoute.Schedule
        NavItem.EMPLOYEES -> AppRoute.Employees
        NavItem.SETTINGS -> AppRoute.Settings
    }

fun NavController.navigateToSection(route: AppRoute, builder: NavOptionsBuilder.() -> Unit = {}) {
    navigate(route) {
        popUpTo(AppRoute.Home)
        launchSingleTop = true
        builder()
    }
}
