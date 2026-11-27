package org.athletica.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import org.athletica.crm.api.schemas.clientapp.ClientAppProfileSchema
import org.athletica.crm.api.schemas.clientapp.ClientAppProfilesResponse
import org.athletica.mobile.api.ClientApi
import org.athletica.mobile.resources.Res
import org.athletica.mobile.resources.chat_soon
import org.athletica.mobile.resources.nav_chat
import org.athletica.mobile.resources.nav_home
import org.athletica.mobile.resources.nav_more
import org.athletica.mobile.resources.nav_schedule
import org.athletica.mobile.session.ProfileKey
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.reflect.KClass

/** Маршруты разделов приложения. */
@Serializable
sealed interface Route {
    /** Главная. */
    @Serializable
    data object Home : Route

    /** Расписание. */
    @Serializable
    data object Schedule : Route

    /** Переписка. */
    @Serializable
    data object Chat : Route

    /** «Ещё». */
    @Serializable
    data object More : Route

    /** История посещений. */
    @Serializable
    data object Visits : Route

    /** Абонементы. */
    @Serializable
    data object Memberships : Route

    /** Баланс. */
    @Serializable
    data object Balance : Route

    /** Устройства аккаунта. */
    @Serializable
    data object Devices : Route

    /** Переключатель профилей. */
    @Serializable
    data object Switcher : Route
}

/** Пункт нижнего меню: раздел [route] с подписью [label] и значком [icon]. */
private data class Tab(val route: Route, val label: StringResource, val icon: ImageVector)

/** Нижнее меню: «Главная · Расписание · Чат · Ещё». */
private val TABS =
    listOf(
        Tab(Route.Home, Res.string.nav_home, Icons.Filled.Home),
        Tab(Route.Schedule, Res.string.nav_schedule, Icons.Filled.DateRange),
        Tab(Route.Chat, Res.string.nav_chat, Icons.Filled.Email),
        Tab(Route.More, Res.string.nav_more, Icons.Filled.Menu),
    )

/**
 * Разделы выбранного профиля [profile] с нижним меню через [api]. [profiles] — все профили для
 * переключателя, [onSelect] — выбор другого профиля, [onInvitations] — новые приглашения,
 * [onSignedOut] — выход с устройства.
 */
@Composable
fun MainScreen(
    api: ClientApi,
    links: AppLinks,
    profiles: ClientAppProfilesResponse,
    profile: ClientAppProfileSchema,
    onSelect: (ProfileKey) -> Unit,
    onInvitations: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    // Граф навигации не должен зависеть от профиля: новый граф сбросил бы стек экранов.
    val current by rememberUpdatedState(profile)
    val all by rememberUpdatedState(profiles)

    fun open(route: Route) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                TABS.forEach { tab ->
                    NavigationBarItem(
                        selected = entry?.destination?.hasRoute(tab.route::class as KClass<*>) == true,
                        onClick = { open(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Route.Home, modifier = Modifier.padding(padding)) {
            composable<Route.Home> {
                HomeScreen(api, current, all.newInvitations, onSwitch = { nav.navigate(Route.Switcher) }, onOpen = { nav.navigate(it) })
            }
            composable<Route.Schedule> { ScheduleScreen(api, current.key) }
            composable<Route.Chat> { Box(Modifier.padding(24.dp)) { Text(stringResource(Res.string.chat_soon)) } }
            composable<Route.More> {
                MoreScreen(api, links, all.newInvitations, onOpen = { nav.navigate(it) }, onSignedOut = onSignedOut)
            }
            composable<Route.Visits> { VisitsScreen(api, current.key) }
            composable<Route.Memberships> { MembershipsScreen(api, current.key) }
            composable<Route.Balance> { BalanceScreen(api, current.key) }
            composable<Route.Devices> { DevicesScreen(api) }
            composable<Route.Switcher> {
                SwitcherScreen(
                    all,
                    current.key,
                    onSelect = {
                        onSelect(it)
                        nav.popBackStack(Route.Home, inclusive = false)
                    },
                    onInvitations = onInvitations,
                )
            }
        }
    }
}
