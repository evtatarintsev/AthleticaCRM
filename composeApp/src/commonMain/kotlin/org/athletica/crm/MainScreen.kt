package org.athletica.crm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.AuthMeResponse
import org.athletica.crm.api.schemas.OrgInfo
import org.athletica.crm.api.schemas.notifications.MarkNotificationsReadRequest
import org.athletica.crm.components.avatar.Avatar
import org.athletica.crm.components.clients.ClientCreateScreen
import org.athletica.crm.components.clients.ClientDetailScreen
import org.athletica.crm.components.clients.ClientEditScreenLoader
import org.athletica.crm.components.clients.ClientsScreen
import org.athletica.crm.components.employees.EmployeeCreateScreen
import org.athletica.crm.components.employees.EmployeeDetailScreen
import org.athletica.crm.components.employees.EmployeeEditScreenLoader
import org.athletica.crm.components.employees.EmployeesScreen
import org.athletica.crm.components.groups.GroupCreateScreen
import org.athletica.crm.components.groups.GroupsScreen
import org.athletica.crm.components.notifications.AppNotification
import org.athletica.crm.components.notifications.NotificationBell
import org.athletica.crm.components.notifications.NotificationLink
import org.athletica.crm.components.settings.ActivityLogScreen
import org.athletica.crm.components.settings.ChangePasswordScreen
import org.athletica.crm.components.settings.ClientSourcesScreen
import org.athletica.crm.components.settings.DisciplinesScreen
import org.athletica.crm.components.settings.EditProfileScreen
import org.athletica.crm.components.settings.OrgBasicSettingsScreen
import org.athletica.crm.components.settings.OrgSettingsScreen
import org.athletica.crm.components.settings.RolesScreen
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_add_client
import org.athletica.crm.generated.resources.action_collapse_menu
import org.athletica.crm.generated.resources.action_logout
import org.athletica.crm.generated.resources.action_open_menu
import org.athletica.crm.generated.resources.action_toggle_nav
import org.athletica.crm.generated.resources.app_name
import org.athletica.crm.generated.resources.cd_app_logo
import org.athletica.crm.generated.resources.hint_search
import org.athletica.crm.generated.resources.label_balance_value
import org.athletica.crm.generated.resources.nav_clients
import org.athletica.crm.generated.resources.nav_employees
import org.athletica.crm.generated.resources.nav_groups
import org.athletica.crm.generated.resources.nav_home
import org.athletica.crm.generated.resources.nav_schedule
import org.athletica.crm.generated.resources.nav_settings
import org.athletica.crm.navigation.AppRoute
import org.athletica.crm.navigation.navigateToSection
import org.athletica.crm.navigation.toRoute
import org.athletica.crm.ui.WindowSize
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import kotlin.uuid.Uuid.Companion.parse
import org.athletica.crm.api.schemas.notifications.NotificationItem as ApiNotificationItem

/** Пункт бокового меню навигации. */
enum class NavItem(
    /** Иконка раздела. */
    val icon: ImageVector,
) {
    /** Главная страница. */
    HOME(Icons.Default.Home),

    /** Раздел клиентов. */
    CLIENTS(Icons.Default.Person),

    /** Раздел групп. */
    GROUPS(Icons.Default.Group),

    /** Расписание занятий. */
    SCHEDULE(Icons.Default.DateRange),

    /** Сотрудники организации. */
    EMPLOYEES(Icons.Default.Badge),

    /** Настройки приложения. */
    SETTINGS(Icons.Default.Settings),
}

/** Возвращает локализованное название пункта меню. */
@Composable
fun NavItem.label(): String =
    when (this) {
        NavItem.HOME -> stringResource(Res.string.nav_home)
        NavItem.CLIENTS -> stringResource(Res.string.nav_clients)
        NavItem.GROUPS -> stringResource(Res.string.nav_groups)
        NavItem.SCHEDULE -> stringResource(Res.string.nav_schedule)
        NavItem.EMPLOYEES -> stringResource(Res.string.nav_employees)
        NavItem.SETTINGS -> stringResource(Res.string.nav_settings)
    }

/**
 * Главный экран приложения с адаптивным боковым меню.
 * Использует [BoxWithConstraints] для выбора режима отображения:
 * мобильный (< 600dp), свёрнутый (600–1200dp) и развёрнутый (≥ 1200dp).
 */
@Composable
fun MainScreen(
    api: ApiClient,
    navController: NavHostController,
    initialRoute: AppRoute = AppRoute.Home,
    onLogout: () -> Unit = {},
) {
    var isSidebarExpanded by remember { mutableStateOf(true) }
    var notifications by remember { mutableStateOf<List<AppNotification>>(emptyList()) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(initialRoute) {
        if (initialRoute != AppRoute.Home) {
            navController.navigate(initialRoute)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            api.notificationList().fold(
                ifLeft = { /* ошибка — оставляем пустой список */ },
                ifRight = { response -> notifications = response.notifications.map { it.toAppNotification() } },
            )
            delay(60.seconds)
        }
    }

    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    val selectedItem: NavItem =
        when {
            currentRoute?.contains("AppRoute.Client") == true -> NavItem.CLIENTS
            currentRoute?.contains("AppRoute.Group") == true -> NavItem.GROUPS
            currentRoute?.contains("AppRoute.Schedule") == true -> NavItem.SCHEDULE
            currentRoute?.contains("AppRoute.Employee") == true -> NavItem.EMPLOYEES
            currentRoute?.contains("AppRoute.Settings") == true -> NavItem.SETTINGS
            else -> NavItem.HOME
        }

    fun onNotificationLink(link: NotificationLink) {
        when (link) {
            is NotificationLink.ToClient -> navController.navigate(AppRoute.ClientDetail(link.clientId.toString()))
            NotificationLink.ToSchedule -> navController.navigateToSection(AppRoute.Schedule)
            NotificationLink.ToClients -> navController.navigateToSection(AppRoute.Clients)
            NotificationLink.ToGroups -> navController.navigateToSection(AppRoute.Groups)
        }
    }

    fun onMarkNotificationRead(id: Uuid) {
        notifications = notifications.map { if (it.id == id) it.copy(isRead = true) else it }
        scope.launch { api.markNotificationsRead(MarkNotificationsReadRequest(listOf(id))) }
    }

    fun onMarkAllNotificationsRead() {
        notifications = notifications.map { it.copy(isRead = true) }
        scope.launch { api.markAllNotificationsRead() }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val windowSize = WindowSize.fromWidth(maxWidth)
        when {
            windowSize == WindowSize.COMPACT -> {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            DrawerContent(
                                api = api,
                                selectedItem = selectedItem,
                                expanded = true,
                                onItemSelected = { item ->
                                    navController.navigateToSection(item.toRoute())
                                    scope.launch { drawerState.close() }
                                },
                            )
                        }
                    },
                ) {
                    Scaffold(
                        topBar = {
                            MainTopAppBar(
                                showMenuButton = true,
                                windowSize = windowSize,
                                notifications = notifications,
                                onMarkNotificationRead = ::onMarkNotificationRead,
                                onMarkAllNotificationsRead = ::onMarkAllNotificationsRead,
                                onNotificationNavigate = ::onNotificationLink,
                                onMenuClick = { scope.launch { drawerState.open() } },
                                onLogout = onLogout,
                                extraActions = {
                                    TopBarActions(selectedItem, onCreateClient = { navController.navigate(AppRoute.ClientCreate) })
                                },
                            )
                        },
                    ) { innerPadding ->
                        AppNavHost(navController, api, Modifier.padding(innerPadding))
                    }
                }
            }

            windowSize == WindowSize.MEDIUM || !isSidebarExpanded -> {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(
                        header = {
                            IconButton(onClick = { isSidebarExpanded = !isSidebarExpanded }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = stringResource(Res.string.action_toggle_nav),
                                )
                            }
                        },
                    ) {
                        Spacer(Modifier.height(8.dp))
                        NavItem.entries.forEach { item ->
                            NavigationRailItem(
                                selected = selectedItem == item,
                                onClick = { navController.navigateToSection(item.toRoute()) },
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label(),
                                    )
                                },
                            )
                        }
                    }
                    Scaffold(
                        modifier = Modifier.weight(1f),
                        topBar = {
                            MainTopAppBar(
                                showMenuButton = false,
                                windowSize = windowSize,
                                notifications = notifications,
                                onMarkNotificationRead = ::onMarkNotificationRead,
                                onMarkAllNotificationsRead = ::onMarkAllNotificationsRead,
                                onNotificationNavigate = ::onNotificationLink,
                                onMenuClick = {},
                                onLogout = onLogout,
                                extraActions = {
                                    TopBarActions(selectedItem, onCreateClient = { navController.navigate(AppRoute.ClientCreate) })
                                },
                            )
                        },
                    ) { innerPadding ->
                        AppNavHost(navController, api, Modifier.padding(innerPadding))
                    }
                }
            }

            else -> {
                PermanentNavigationDrawer(
                    drawerContent = {
                        PermanentDrawerSheet {
                            DrawerContent(
                                api = api,
                                selectedItem = selectedItem,
                                expanded = true,
                                onItemSelected = { navController.navigateToSection(it.toRoute()) },
                                onToggle = { isSidebarExpanded = false },
                            )
                        }
                    },
                ) {
                    Scaffold(
                        topBar = {
                            MainTopAppBar(
                                showMenuButton = false,
                                windowSize = windowSize,
                                notifications = notifications,
                                onMarkNotificationRead = ::onMarkNotificationRead,
                                onMarkAllNotificationsRead = ::onMarkAllNotificationsRead,
                                onNotificationNavigate = ::onNotificationLink,
                                onMenuClick = {},
                                onLogout = onLogout,
                                extraActions = {
                                    TopBarActions(selectedItem, onCreateClient = { navController.navigate(AppRoute.ClientCreate) })
                                },
                            )
                        },
                    ) { innerPadding ->
                        AppNavHost(navController, api, Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    api: ApiClient,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = AppRoute.Home,
        modifier = modifier,
    ) {
        composable<AppRoute.Home> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(NavItem.HOME.label(), style = MaterialTheme.typography.headlineMedium)
            }
        }

        composable<AppRoute.Schedule> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(NavItem.SCHEDULE.label(), style = MaterialTheme.typography.headlineMedium)
            }
        }

        // ── Clients ────────────────────────────────────────────────────────────

        composable<AppRoute.Clients> {
            ClientsScreen(
                api = api,
                onNavigateToCreate = { navController.navigate(AppRoute.ClientCreate) },
                onClientClick = { id -> navController.navigate(AppRoute.ClientDetail(id.toString())) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable<AppRoute.ClientCreate> {
            ClientCreateScreen(
                api = api,
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() },
            )
        }

        composable<AppRoute.ClientDetail> { entry ->
            val route = entry.toRoute<AppRoute.ClientDetail>()
            ClientDetailScreen(
                clientId = ClientId(parse(route.id)),
                api = api,
                onBack = { navController.popBackStack() },
                onEdit = { client -> navController.navigate(AppRoute.ClientEdit(client.id.toString())) },
            )
        }

        composable<AppRoute.ClientEdit> { entry ->
            val route = entry.toRoute<AppRoute.ClientEdit>()
            ClientEditScreenLoader(
                clientId = ClientId(parse(route.id)),
                api = api,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        // ── Groups ─────────────────────────────────────────────────────────────

        composable<AppRoute.Groups> {
            GroupsScreen(
                api = api,
                onNavigateToCreate = { navController.navigate(AppRoute.GroupCreate) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable<AppRoute.GroupCreate> {
            GroupCreateScreen(
                api = api,
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() },
            )
        }

        // ── Employees ──────────────────────────────────────────────────────────

        composable<AppRoute.Employees> {
            EmployeesScreen(
                api = api,
                onNavigateToCreate = { navController.navigate(AppRoute.EmployeeCreate) },
                onEmployeeClick = { id -> navController.navigate(AppRoute.EmployeeDetail(id.toString())) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable<AppRoute.EmployeeCreate> {
            EmployeeCreateScreen(
                api = api,
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() },
            )
        }

        composable<AppRoute.EmployeeDetail> { entry ->
            val route = entry.toRoute<AppRoute.EmployeeDetail>()
            EmployeeDetailScreen(
                employeeId = EmployeeId(parse(route.id)),
                api = api,
                onBack = { navController.popBackStack() },
                onEdit = { employee -> navController.navigate(AppRoute.EmployeeEdit(employee.id.toString())) },
            )
        }

        composable<AppRoute.EmployeeEdit> { entry ->
            val route = entry.toRoute<AppRoute.EmployeeEdit>()
            EmployeeEditScreenLoader(
                employeeId = EmployeeId(parse(route.id)),
                api = api,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        // ── Settings ───────────────────────────────────────────────────────────

        composable<AppRoute.Settings> {
            OrgSettingsScreen(
                onNavigateToBasicSettings = { navController.navigate(AppRoute.SettingsBasic) },
                onNavigateToClientSources = { navController.navigate(AppRoute.SettingsClientSources) },
                onNavigateToDisciplines = { navController.navigate(AppRoute.SettingsDisciplines) },
                onNavigateToActivityLog = { navController.navigate(AppRoute.SettingsActivityLog) },
                onNavigateToChangePassword = { navController.navigate(AppRoute.SettingsChangePassword) },
                onNavigateToEditProfile = { navController.navigate(AppRoute.SettingsEditProfile) },
                onNavigateToRoles = { navController.navigate(AppRoute.SettingsRoles) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable<AppRoute.SettingsBasic> {
            OrgBasicSettingsScreen(api = api, onBack = { navController.popBackStack() })
        }

        composable<AppRoute.SettingsClientSources> {
            ClientSourcesScreen(onBack = { navController.popBackStack() })
        }

        composable<AppRoute.SettingsDisciplines> {
            DisciplinesScreen(api = api, onBack = { navController.popBackStack() })
        }

        composable<AppRoute.SettingsRoles> {
            RolesScreen(api = api, onBack = { navController.popBackStack() })
        }

        composable<AppRoute.SettingsActivityLog> {
            ActivityLogScreen(api = api, onBack = { navController.popBackStack() })
        }

        composable<AppRoute.SettingsChangePassword> {
            ChangePasswordScreen(api = api, onBack = { navController.popBackStack() })
        }

        composable<AppRoute.SettingsEditProfile> {
            EditProfileScreen(api = api, onBack = { navController.popBackStack() })
        }
    }
}

/**
 * Шапка аккаунта в боковой панели навигации.
 */
@Composable
private fun DrawerAccountHeader(api: ApiClient) {
    val noResponse =
        AuthMeResponse(
            UserId.new(), "", "", null,
            OrgInfo("", null),
        )
    var me by remember { mutableStateOf(noResponse) }

    LaunchedEffect(Unit) {
        api.me().onRight { me = it }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Avatar(me.avatarId, me.name, api)
        }

        Spacer(Modifier.width(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = me.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = me.orgInfo.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            me.orgInfo.balance?.let { balance ->
                Text(
                    text = stringResource(Res.string.label_balance_value, balance),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Содержимое боковой панели навигации.
 */
@Composable
private fun DrawerContent(
    api: ApiClient,
    selectedItem: NavItem,
    expanded: Boolean,
    onItemSelected: (NavItem) -> Unit,
    onToggle: () -> Unit = {},
) {
    Column(
        modifier =
            Modifier
                .fillMaxHeight()
                .width(280.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = stringResource(Res.string.cd_app_logo),
                tint = MaterialTheme.colorScheme.primary,
            )
            if (expanded) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = stringResource(Res.string.action_collapse_menu),
                )
            }
        }

        if (expanded) {
            DrawerAccountHeader(api)
        }

        HorizontalDivider()

        Spacer(Modifier.height(8.dp))

        NavItem.entries.forEach { item ->
            NavigationDrawerItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label(),
                    )
                },
                label = { Text(item.label()) },
                selected = selectedItem == item,
                onClick = { onItemSelected(item) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }
    }
}

/**
 * Контекстные action-кнопки top bar для текущего [selectedItem].
 */
@Composable
private fun RowScope.TopBarActions(
    selectedItem: NavItem,
    onCreateClient: () -> Unit,
) {
    when (selectedItem) {
        NavItem.CLIENTS ->
            IconButton(onClick = onCreateClient) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(Res.string.action_add_client),
                )
            }
        else -> Unit
    }
}

/**
 * Верхняя панель приложения с поиском, уведомлениями и кнопкой открытия меню.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopAppBar(
    showMenuButton: Boolean,
    windowSize: WindowSize,
    notifications: List<AppNotification>,
    onMarkNotificationRead: (Uuid) -> Unit,
    onMarkAllNotificationsRead: () -> Unit,
    onNotificationNavigate: (NotificationLink) -> Unit,
    onMenuClick: () -> Unit,
    onLogout: () -> Unit,
    extraActions: @Composable RowScope.() -> Unit = {},
) {
    var searchQuery by remember { mutableStateOf("") }

    TopAppBar(
        title = {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(Res.string.hint_search), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(0.5f),
            )
        },
        navigationIcon = {
            if (showMenuButton) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = stringResource(Res.string.action_open_menu),
                    )
                }
            }
        },
        actions = {
            extraActions()
            NotificationBell(
                notifications = notifications,
                windowSize = windowSize,
                onMarkRead = onMarkNotificationRead,
                onMarkAllRead = onMarkAllNotificationsRead,
                onNavigate = onNotificationNavigate,
            )
            IconButton(onClick = onLogout) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = stringResource(Res.string.action_logout),
                )
            }
        },
    )
}

/** Конвертирует API-схему [ApiNotificationItem] в UI-модель [AppNotification]. */
private fun ApiNotificationItem.toAppNotification() =
    AppNotification(
        id = id,
        title = title,
        body = body,
        isRead = isRead,
        createdAt = createdAt,
        link = null,
    )
