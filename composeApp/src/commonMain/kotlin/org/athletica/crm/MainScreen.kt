package org.athletica.crm

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
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
import org.athletica.crm.api.schemas.notifications.MarkNotificationsReadRequest
import org.athletica.crm.components.account.AccountMenu
import org.athletica.crm.components.account.AccountMenuLink
import org.athletica.crm.components.clients.ClientCreateScreen
import org.athletica.crm.components.clients.ClientDetailScreen
import org.athletica.crm.components.clients.ClientEditScreenLoader
import org.athletica.crm.components.clients.ClientPaymentHistoryScreen
import org.athletica.crm.components.clients.ClientSubscriptionHistoryScreen
import org.athletica.crm.components.clients.ClientVisitHistoryScreen
import org.athletica.crm.components.clients.ClientsScreen
import org.athletica.crm.components.clients.ExportScreen
import org.athletica.crm.components.clients.subscription.IssueSubscriptionScreen
import org.athletica.crm.components.employees.EmployeeCreateScreen
import org.athletica.crm.components.employees.EmployeeDetailScreen
import org.athletica.crm.components.employees.EmployeeEditScreenLoader
import org.athletica.crm.components.employees.EmployeesScreen
import org.athletica.crm.components.groups.GroupCreateScreen
import org.athletica.crm.components.groups.GroupDetailScreen
import org.athletica.crm.components.groups.GroupEditScreenLoader
import org.athletica.crm.components.groups.GroupsScreen
import org.athletica.crm.components.home.HomeScreen
import org.athletica.crm.components.messaging.ConversationScreen
import org.athletica.crm.components.notifications.AppNotification
import org.athletica.crm.components.notifications.NotificationBell
import org.athletica.crm.components.notifications.NotificationLink
import org.athletica.crm.components.settings.ActivityLogScreen
import org.athletica.crm.components.settings.BranchesScreen
import org.athletica.crm.components.settings.ChangePasswordScreen
import org.athletica.crm.components.settings.ClientAdditionalAttributesScreen
import org.athletica.crm.components.settings.ClientSourcesScreen
import org.athletica.crm.components.settings.DisciplinesScreen
import org.athletica.crm.components.settings.DisplaySettingsViewModel
import org.athletica.crm.components.settings.EditProfileScreen
import org.athletica.crm.components.settings.HallsScreen
import org.athletica.crm.components.settings.OrgBasicSettingsScreen
import org.athletica.crm.components.settings.OrgSettingsScreen
import org.athletica.crm.components.settings.RolesScreen
import org.athletica.crm.components.settings.SwitchBranchScreen
import org.athletica.crm.components.settings.channels.ChannelsScreen
import org.athletica.crm.components.settings.clientimport.ClientImportScreen
import org.athletica.crm.components.settings.orgbalance.OrgBalanceScreen
import org.athletica.crm.components.tasks.TaskCreateScreen
import org.athletica.crm.components.tasks.TaskDetailScreen
import org.athletica.crm.components.tasks.TasksScreen
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_collapse_menu
import org.athletica.crm.generated.resources.action_download_desktop
import org.athletica.crm.generated.resources.action_open_menu
import org.athletica.crm.generated.resources.action_toggle_nav
import org.athletica.crm.generated.resources.app_name
import org.athletica.crm.generated.resources.cd_app_logo
import org.athletica.crm.generated.resources.download_desktop_subtitle
import org.athletica.crm.generated.resources.nav_clients
import org.athletica.crm.generated.resources.nav_employees
import org.athletica.crm.generated.resources.nav_groups
import org.athletica.crm.generated.resources.nav_home
import org.athletica.crm.generated.resources.nav_schedule
import org.athletica.crm.generated.resources.nav_settings
import org.athletica.crm.generated.resources.nav_tasks
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

    /** Трекер задач. */
    TASKS(Icons.Default.CheckCircle),

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
        NavItem.TASKS -> stringResource(Res.string.nav_tasks)
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
    val topBarController = remember { org.athletica.crm.ui.list.ListPageTopBarController() }
    var isSidebarExpanded by remember { mutableStateOf(true) }
    var notifications by remember { mutableStateOf<List<AppNotification>>(emptyList()) }
    var me by remember { mutableStateOf<AuthMeResponse?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val reloadMe: () -> Unit = {
        scope.launch { api.profile.me().onRight { me = it } }
    }

    val displaySettingsVm =
        remember {
            DisplaySettingsViewModel(api, scope)
        }

    LaunchedEffect(initialRoute) {
        if (initialRoute != AppRoute.Home) {
            navController.navigate(initialRoute)
        }
    }

    LaunchedEffect(Unit) {
        api.profile.me().onRight { me = it }
    }

    LaunchedEffect(Unit) {
        while (true) {
            api.notifications.list().fold(
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
            currentRoute?.contains("AppRoute.Task") == true -> NavItem.TASKS
            currentRoute?.contains("AppRoute.Settings") == true -> NavItem.SETTINGS
            else -> NavItem.HOME
        }

    val selectedItemLabel = selectedItem.label()
    LaunchedEffect(selectedItemLabel) { topBarController.set(selectedItemLabel) }

    fun onNotificationLink(link: NotificationLink) {
        when (link) {
            is NotificationLink.ToClient -> navController.navigate(AppRoute.ClientDetail(link.clientId.toString()))
            NotificationLink.ToSchedule -> navController.navigateToSection(AppRoute.Schedule)
            NotificationLink.ToClients -> navController.navigateToSection(AppRoute.Clients)
            NotificationLink.ToGroups -> navController.navigateToSection(AppRoute.Groups)
        }
    }

    fun onAccountMenuLink(link: AccountMenuLink) {
        when (link) {
            AccountMenuLink.EditProfile -> navController.navigate(AppRoute.SettingsEditProfile)
            AccountMenuLink.SwitchBranch -> navController.navigate(AppRoute.SettingsSwitchBranch)
            AccountMenuLink.OrgBalance -> navController.navigate(AppRoute.SettingsOrgBalance)
        }
    }

    fun onMarkNotificationRead(id: Uuid) {
        notifications = notifications.map { if (it.id == id) it.copy(isRead = true) else it }
        scope.launch { api.notifications.markRead(MarkNotificationsReadRequest(listOf(id))) }
    }

    fun onMarkAllNotificationsRead() {
        notifications = notifications.map { it.copy(isRead = true) }
        scope.launch { api.notifications.markAllRead() }
    }

    androidx.compose.runtime.CompositionLocalProvider(org.athletica.crm.ui.list.LocalListPageTopBar provides topBarController) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val windowSize = WindowSize.fromWidth(maxWidth)
            when {
                windowSize == WindowSize.COMPACT -> {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            ModalDrawerSheet {
                                DrawerContent(
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
                                    api = api,
                                    me = me,
                                    notifications = notifications,
                                    onMarkNotificationRead = ::onMarkNotificationRead,
                                    onMarkAllNotificationsRead = ::onMarkAllNotificationsRead,
                                    onNotificationNavigate = ::onNotificationLink,
                                    onAccountNavigate = ::onAccountMenuLink,
                                    onMenuClick = { scope.launch { drawerState.open() } },
                                    onLogout = onLogout,
                                    extraActions = {
                                        TopBarActions(selectedItem, onCreateClient = { navController.navigate(AppRoute.ClientCreate) })
                                    },
                                )
                            },
                        ) { innerPadding ->
                            AppNavHost(navController, api, me, reloadMe, displaySettingsVm, windowSize, Modifier.padding(innerPadding))
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
                                    api = api,
                                    me = me,
                                    notifications = notifications,
                                    onMarkNotificationRead = ::onMarkNotificationRead,
                                    onMarkAllNotificationsRead = ::onMarkAllNotificationsRead,
                                    onNotificationNavigate = ::onNotificationLink,
                                    onAccountNavigate = ::onAccountMenuLink,
                                    onMenuClick = {},
                                    onLogout = onLogout,
                                    extraActions = {
                                        TopBarActions(selectedItem, onCreateClient = { navController.navigate(AppRoute.ClientCreate) })
                                    },
                                )
                            },
                        ) { innerPadding ->
                            AppNavHost(navController, api, me, reloadMe, displaySettingsVm, windowSize, Modifier.padding(innerPadding))
                        }
                    }
                }

                else -> {
                    PermanentNavigationDrawer(
                        drawerContent = {
                            PermanentDrawerSheet {
                                DrawerContent(
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
                                    api = api,
                                    me = me,
                                    notifications = notifications,
                                    onMarkNotificationRead = ::onMarkNotificationRead,
                                    onMarkAllNotificationsRead = ::onMarkAllNotificationsRead,
                                    onNotificationNavigate = ::onNotificationLink,
                                    onAccountNavigate = ::onAccountMenuLink,
                                    onMenuClick = {},
                                    onLogout = onLogout,
                                    extraActions = {
                                        TopBarActions(selectedItem, onCreateClient = { navController.navigate(AppRoute.ClientCreate) })
                                    },
                                )
                            },
                        ) { innerPadding ->
                            AppNavHost(navController, api, me, reloadMe, displaySettingsVm, windowSize, Modifier.padding(innerPadding))
                        }
                    }
                }
            }
        }
    } // CompositionLocalProvider
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    api: ApiClient,
    me: AuthMeResponse?,
    reloadMe: () -> Unit,
    displaySettingsVm: DisplaySettingsViewModel,
    windowSize: WindowSize,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = AppRoute.Home,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
        modifier = modifier,
    ) {
        composable<AppRoute.Home> {
            HomeScreen(
                api = api,
                onClientClick = { id -> navController.navigate(AppRoute.ClientDetail(id.toString())) },
                onShowAllClients = { navController.navigateToSection(AppRoute.Clients) },
                modifier = Modifier.fillMaxSize(),
            )
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
                displaySettingsVm = displaySettingsVm,
                windowSize = windowSize,
                onNavigateToCreate = { navController.navigate(AppRoute.ClientCreate) },
                onClientClick = { id -> navController.navigate(AppRoute.ClientDetail(id.toString())) },
                onNavigateToExport = { selectedIds ->
                    navController.navigate(AppRoute.ClientExport(selectedIds.map { it.value.toString() }))
                },
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

        composable<AppRoute.ClientExport> { entry ->
            val route = entry.toRoute<AppRoute.ClientExport>()
            val selectedIds =
                route.selectedIds.mapNotNull { id ->
                    runCatching { org.athletica.crm.core.entityids.ClientId(kotlin.uuid.Uuid.parse(id)) }.getOrNull()
                }
            ExportScreen(
                api = api,
                selectedClientIds = selectedIds,
                onBack = { navController.popBackStack() },
            )
        }

        composable<AppRoute.ClientDetail> { entry ->
            val route = entry.toRoute<AppRoute.ClientDetail>()
            ClientDetailScreen(
                clientId = ClientId(parse(route.id)),
                api = api,
                currentEmployeeId = me?.employeeId,
                onBack = { navController.popBackStack() },
                onEdit = { client -> navController.navigate(AppRoute.ClientEdit(client.id.toString())) },
                onOpenVisitHistory = { navController.navigate(AppRoute.ClientVisitHistory(route.id)) },
                onOpenPaymentHistory = { navController.navigate(AppRoute.ClientPaymentHistory(route.id)) },
                onOpenSubscriptionHistory = {
                    navController.navigate(AppRoute.ClientSubscriptionHistory(route.id))
                },
                onIssueSubscription = { navController.navigate(AppRoute.IssueSubscription(route.id)) },
                onOpenMessages = { navController.navigate(AppRoute.Conversation(route.id)) },
            )
        }

        composable<AppRoute.Conversation> { entry ->
            val route = entry.toRoute<AppRoute.Conversation>()
            ConversationScreen(
                clientId = ClientId(parse(route.clientId)),
                api = api,
                onBack = { navController.popBackStack() },
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

        composable<AppRoute.ClientVisitHistory> { entry ->
            val route = entry.toRoute<AppRoute.ClientVisitHistory>()
            ClientVisitHistoryScreen(
                clientId = ClientId(parse(route.id)),
                onBack = { navController.popBackStack() },
            )
        }

        composable<AppRoute.ClientPaymentHistory> { entry ->
            val route = entry.toRoute<AppRoute.ClientPaymentHistory>()
            ClientPaymentHistoryScreen(
                clientId = ClientId(parse(route.id)),
                onBack = { navController.popBackStack() },
            )
        }

        composable<AppRoute.ClientSubscriptionHistory> { entry ->
            val route = entry.toRoute<AppRoute.ClientSubscriptionHistory>()
            ClientSubscriptionHistoryScreen(
                clientId = ClientId(parse(route.id)),
                onBack = { navController.popBackStack() },
            )
        }

        composable<AppRoute.IssueSubscription> { entry ->
            val route = entry.toRoute<AppRoute.IssueSubscription>()
            IssueSubscriptionScreen(
                clientId = ClientId(parse(route.clientId)),
                api = api,
                onBack = { navController.popBackStack() },
                onIssued = { navController.popBackStack() },
            )
        }

        // ── Groups ─────────────────────────────────────────────────────────────

        composable<AppRoute.Groups> {
            GroupsScreen(
                api = api,
                displaySettingsVm = displaySettingsVm,
                windowSize = windowSize,
                onNavigateToCreate = { navController.navigate(AppRoute.GroupCreate) },
                onGroupClick = { id -> navController.navigate(AppRoute.GroupDetail(id.toString())) },
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

        composable<AppRoute.GroupDetail> { entry ->
            val route = entry.toRoute<AppRoute.GroupDetail>()
            GroupDetailScreen(
                groupId = GroupId(parse(route.id)),
                api = api,
                onBack = { navController.popBackStack() },
                onEdit = { group -> navController.navigate(AppRoute.GroupEdit(group.id.toString())) },
            )
        }

        composable<AppRoute.GroupEdit> { entry ->
            val route = entry.toRoute<AppRoute.GroupEdit>()
            GroupEditScreenLoader(
                groupId = GroupId(parse(route.id)),
                api = api,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        // ── Employees ──────────────────────────────────────────────────────────

        composable<AppRoute.Employees> {
            EmployeesScreen(
                api = api,
                displaySettingsVm = displaySettingsVm,
                windowSize = windowSize,
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
                onNavigateToOrgBalance = { navController.navigate(AppRoute.SettingsOrgBalance) },
                onNavigateToBranches = { navController.navigate(AppRoute.SettingsBranches) },
                onNavigateToHalls = { navController.navigate(AppRoute.SettingsHalls) },
                onNavigateToClientSources = { navController.navigate(AppRoute.SettingsClientSources) },
                onNavigateToClientAdditionalAttributes = { navController.navigate(AppRoute.SettingsClientAdditionalAttributes) },
                onNavigateToClientImport = { navController.navigate(AppRoute.SettingsClientImport) },
                onNavigateToDisciplines = { navController.navigate(AppRoute.SettingsDisciplines) },
                onNavigateToActivityLog = { navController.navigate(AppRoute.SettingsActivityLog) },
                onNavigateToChangePassword = { navController.navigate(AppRoute.SettingsChangePassword) },
                onNavigateToEditProfile = { navController.navigate(AppRoute.SettingsEditProfile) },
                onNavigateToSwitchBranch = { navController.navigate(AppRoute.SettingsSwitchBranch) },
                onNavigateToRoles = { navController.navigate(AppRoute.SettingsRoles) },
                onNavigateToChannels = { navController.navigate(AppRoute.SettingsChannels) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable<AppRoute.SettingsChannels> {
            ChannelsScreen(api = api, onBack = { navController.popBackStack() })
        }

        composable<AppRoute.SettingsBranches> {
            BranchesScreen(api = api, onBack = { navController.popBackStack() })
        }

        composable<AppRoute.SettingsBasic> {
            OrgBasicSettingsScreen(api = api, onBack = { navController.popBackStack() })
        }

        composable<AppRoute.SettingsOrgBalance> {
            OrgBalanceScreen(api = api, onBack = { navController.popBackStack() })
        }

        composable<AppRoute.SettingsClientSources> {
            ClientSourcesScreen(api = api, onBack = { navController.popBackStack() })
        }

        composable<AppRoute.SettingsClientAdditionalAttributes> {
            ClientAdditionalAttributesScreen(api = api, onBack = { navController.popBackStack() })
        }

        composable<AppRoute.SettingsClientImport> {
            ClientImportScreen(api = api, onBack = { navController.popBackStack() })
        }

        composable<AppRoute.SettingsBranches> {
            BranchesScreen(api = api, onBack = { navController.popBackStack() })
        }

        composable<AppRoute.SettingsBasic> {
            OrgBasicSettingsScreen(api = api, onBack = { navController.popBackStack() })
        }

        composable<AppRoute.SettingsDisciplines> {
            DisciplinesScreen(api = api, onBack = { navController.popBackStack() })
        }

        composable<AppRoute.SettingsHalls> {
            HallsScreen(api = api, onBack = { navController.popBackStack() })
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

        composable<AppRoute.SettingsSwitchBranch> {
            SwitchBranchScreen(
                api = api,
                currentBranchId = me?.currentBranch?.id,
                onBack = { navController.popBackStack() },
                onSwitched = reloadMe,
            )
        }

        // ── Tasks ─────────────────────────────────────────────────────────────

        composable<AppRoute.Tasks> {
            TasksScreen(
                api = api,
                displaySettingsVm = displaySettingsVm,
                windowSize = windowSize,
                onNavigateToCreate = { navController.navigate(AppRoute.TaskCreate) },
                onTaskClick = { id -> navController.navigate(AppRoute.TaskDetail(id.toString())) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable<AppRoute.TaskCreate> {
            TaskCreateScreen(
                api = api,
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() },
            )
        }

        composable<AppRoute.TaskDetail> { entry ->
            val route = entry.toRoute<AppRoute.TaskDetail>()
            TaskDetailScreen(
                taskId = org.athletica.crm.core.tasks.TaskId(kotlin.uuid.Uuid.parse(route.id)),
                api = api,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/**
 * Содержимое боковой панели навигации.
 */
@Composable
private fun DrawerContent(
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

        if (currentPlatform == PlatformType.WEB) {
            Spacer(Modifier.weight(1f))
            NavigationDrawerItem(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                    )
                },
                label = {
                    Column {
                        Text(stringResource(Res.string.action_download_desktop))
                        Text(
                            text = stringResource(Res.string.download_desktop_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                selected = false,
                onClick = { openUrl("https://github.com/etatarintsev/AthleticaCRM/releases/latest") },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
            Spacer(Modifier.height(8.dp))
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
}

/**
 * Верхняя панель приложения с поиском, уведомлениями и кнопкой открытия меню.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopAppBar(
    showMenuButton: Boolean,
    windowSize: WindowSize,
    api: ApiClient,
    me: AuthMeResponse?,
    notifications: List<AppNotification>,
    onMarkNotificationRead: (Uuid) -> Unit,
    onMarkAllNotificationsRead: () -> Unit,
    onNotificationNavigate: (NotificationLink) -> Unit,
    onAccountNavigate: (AccountMenuLink) -> Unit,
    onMenuClick: () -> Unit,
    onLogout: () -> Unit,
    extraActions: @Composable RowScope.() -> Unit = {},
) {
    val topBarCtrl = org.athletica.crm.ui.list.LocalListPageTopBar.current

    TopAppBar(
        title = {
            Column {
                Text(
                    text = topBarCtrl.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = topBarCtrl.subtitle
                if (sub != null) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
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
            AccountMenu(
                api = api,
                me = me,
                windowSize = windowSize,
                onNavigate = onAccountNavigate,
                onLogout = onLogout,
            )
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
