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
import androidx.compose.runtime.key
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.AuthMeResponse
import org.athletica.crm.api.schemas.clients.ClientDetailResponse
import org.athletica.crm.api.schemas.employees.EmployeeDetailResponse
import org.athletica.crm.api.schemas.notifications.MarkNotificationsReadRequest
import org.athletica.crm.components.avatar.Avatar
import org.athletica.crm.components.clients.ClientCreateScreen
import org.athletica.crm.components.clients.ClientDetailScreen
import org.athletica.crm.components.clients.ClientEditScreen
import org.athletica.crm.components.clients.ClientsScreen
import org.athletica.crm.components.employees.EmployeeCreateScreen
import org.athletica.crm.components.employees.EmployeeDetailScreen
import org.athletica.crm.components.employees.EmployeeEditScreen
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
import org.athletica.crm.ui.WindowSize
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
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
 *
 * Принимает [api] для передачи дочерним экранам и [onLogout] — callback при нажатии кнопки выхода.
 */
@Composable
fun MainScreen(
    api: ApiClient,
    onLogout: () -> Unit = {},
) {
    var selectedItem by remember { mutableStateOf(NavItem.HOME) }
    var isSidebarExpanded by remember { mutableStateOf(true) }
    var selectedClientId by remember { mutableStateOf<ClientId?>(null) }
    var selectedEmployeeId by remember { mutableStateOf<EmployeeId?>(null) }
    var editingEmployee by remember { mutableStateOf<EmployeeDetailResponse?>(null) }
    var notifications by remember { mutableStateOf<List<AppNotification>>(emptyList()) }

    LaunchedEffect(Unit) {
        while (true) {
            api.notificationList().fold(
                ifLeft = { /* ошибка — оставляем пустой список, не мешаем работе */ },
                ifRight = { response -> notifications = response.notifications.map { it.toAppNotification() } },
            )
            delay(60.seconds)
        }
    }

    fun onNotificationLink(link: NotificationLink) {
        when (link) {
            is NotificationLink.ToClient -> selectedClientId = link.clientId
            NotificationLink.ToSchedule -> selectedItem = NavItem.SCHEDULE
            NotificationLink.ToClients -> selectedItem = NavItem.CLIENTS
            NotificationLink.ToGroups -> selectedItem = NavItem.GROUPS
        }
    }
    var showCreateClient by remember { mutableStateOf(false) }
    var editingClient by remember { mutableStateOf<ClientDetailResponse?>(null) }
    var clientsRefreshKey by remember { mutableStateOf(0) }
    var clientDetailRefreshKey by remember { mutableStateOf(0) }
    var showCreateGroup by remember { mutableStateOf(false) }
    var groupsRefreshKey by remember { mutableStateOf(0) }
    var showCreateEmployee by remember { mutableStateOf(false) }
    var employeesRefreshKey by remember { mutableStateOf(0) }
    var showOrgBasicSettings by remember { mutableStateOf(false) }
    var showClientSources by remember { mutableStateOf(false) }
    var showDisciplines by remember { mutableStateOf(false) }
    var showRoles by remember { mutableStateOf(false) }
    var showActivityLog by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var showEditProfile by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Оптимистично помечает уведомление прочитанным в UI и синхронизирует с сервером.
    fun onMarkNotificationRead(id: Uuid) {
        notifications = notifications.map { if (it.id == id) it.copy(isRead = true) else it }
        scope.launch { api.markNotificationsRead(MarkNotificationsReadRequest(listOf(id))) }
    }

    // Оптимистично помечает все уведомления прочитанными в UI и синхронизирует с сервером.
    fun onMarkAllNotificationsRead() {
        notifications = notifications.map { it.copy(isRead = true) }
        scope.launch { api.markAllNotificationsRead() }
    }

    // Экран редактирования клиента накрывает весь экран поверх навигации
    if (editingClient != null) {
        ClientEditScreen(
            client = editingClient!!,
            api = api,
            onBack = { editingClient = null },
            onSaved = { _ ->
                editingClient = null
                clientsRefreshKey++
                clientDetailRefreshKey++
            },
        )
        return
    }

    // Карточка клиента накрывает весь экран поверх навигации
    if (selectedClientId != null) {
        key(selectedClientId, clientDetailRefreshKey) {
            ClientDetailScreen(
                clientId = selectedClientId!!,
                api = api,
                onBack = { selectedClientId = null },
                onEdit = { client -> editingClient = client },
            )
        }
        return
    }

    // Экран редактирования сотрудника накрывает весь экран поверх навигации
    if (editingEmployee != null) {
        EmployeeEditScreen(
            employee = editingEmployee!!,
            api = api,
            onBack = { editingEmployee = null },
            onSaved = {
                editingEmployee = null
                // Refresh employee list and detail
                employeesRefreshKey++
            },
        )
        return
    }

    // Карточка сотрудника накрывает весь экран поверх навигации
    if (selectedEmployeeId != null) {
        EmployeeDetailScreen(
            employeeId = selectedEmployeeId!!,
            api = api,
            onBack = { selectedEmployeeId = null },
            onEdit = { employee -> editingEmployee = employee },
        )
        return
    }

    // Экран создания клиента накрывает весь экран поверх навигации
    if (showCreateClient) {
        ClientCreateScreen(
            api = api,
            onBack = { showCreateClient = false },
            onCreated = {
                clientsRefreshKey++
                showCreateClient = false
            },
        )
        return
    }

    if (showOrgBasicSettings) {
        OrgBasicSettingsScreen(api = api, onBack = { showOrgBasicSettings = false })
        return
    }

    if (showClientSources) {
        ClientSourcesScreen(onBack = { showClientSources = false })
        return
    }

    if (showDisciplines) {
        DisciplinesScreen(api = api, onBack = { showDisciplines = false })
        return
    }

    if (showRoles) {
        RolesScreen(api = api, onBack = { showRoles = false })
        return
    }

    if (showActivityLog) {
        ActivityLogScreen(api = api, onBack = { showActivityLog = false })
        return
    }

    if (showChangePassword) {
        ChangePasswordScreen(api = api, onBack = { showChangePassword = false })
        return
    }

    if (showEditProfile) {
        EditProfileScreen(api = api, onBack = { showEditProfile = false })
        return
    }

    // Экран создания группы накрывает весь экран поверх навигации
    if (showCreateGroup) {
        GroupCreateScreen(
            api = api,
            onBack = { showCreateGroup = false },
            onCreated = {
                groupsRefreshKey++
                showCreateGroup = false
            },
        )
        return
    }

    if (showCreateEmployee) {
        EmployeeCreateScreen(
            api = api,
            onBack = { showCreateEmployee = false },
            onCreated = {
                employeesRefreshKey++
                showCreateEmployee = false
            },
        )
        return
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
                                onItemSelected = {
                                    selectedItem = it
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
                                extraActions = { TopBarActions(selectedItem, onCreateClient = { showCreateClient = true }) },
                            )
                        },
                    ) { innerPadding ->
                        ContentArea(
                            api = api,
                            selectedItem = selectedItem,
                            onClientClick = { selectedClientId = it },
                            onNavigateToCreateClient = { showCreateClient = true },
                            clientsRefreshKey = clientsRefreshKey,
                            onEmployeeClick = { selectedEmployeeId = it },
                            onNavigateToCreateGroup = { showCreateGroup = true },
                            groupsRefreshKey = groupsRefreshKey,
                            onNavigateToCreateEmployee = { showCreateEmployee = true },
                            employeesRefreshKey = employeesRefreshKey,
                            onNavigateToBasicSettings = { showOrgBasicSettings = true },
                            onNavigateToClientSources = { showClientSources = true },
                            onNavigateToDisciplines = { showDisciplines = true },
                            onNavigateToActivityLog = { showActivityLog = true },
                            onNavigateToChangePassword = { showChangePassword = true },
                            onNavigateToEditProfile = { showEditProfile = true },
                            onNavigateToRoles = { showRoles = true },
                            modifier = Modifier.padding(innerPadding),
                        )
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
                                onClick = { selectedItem = item },
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
                                extraActions = { TopBarActions(selectedItem, onCreateClient = { showCreateClient = true }) },
                            )
                        },
                    ) { innerPadding ->
                        ContentArea(
                            api = api,
                            selectedItem = selectedItem,
                            onClientClick = { selectedClientId = it },
                            onNavigateToCreateClient = { showCreateClient = true },
                            clientsRefreshKey = clientsRefreshKey,
                            onEmployeeClick = { selectedEmployeeId = it },
                            onNavigateToCreateGroup = { showCreateGroup = true },
                            groupsRefreshKey = groupsRefreshKey,
                            onNavigateToCreateEmployee = { showCreateEmployee = true },
                            employeesRefreshKey = employeesRefreshKey,
                            onNavigateToBasicSettings = { showOrgBasicSettings = true },
                            onNavigateToClientSources = { showClientSources = true },
                            onNavigateToDisciplines = { showDisciplines = true },
                            onNavigateToActivityLog = { showActivityLog = true },
                            onNavigateToChangePassword = { showChangePassword = true },
                            onNavigateToEditProfile = { showEditProfile = true },
                            onNavigateToRoles = { showRoles = true },
                            modifier = Modifier.padding(innerPadding),
                        )
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
                                onItemSelected = { selectedItem = it },
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
                                extraActions = { TopBarActions(selectedItem, onCreateClient = { showCreateClient = true }) },
                            )
                        },
                    ) { innerPadding ->
                        ContentArea(
                            api = api,
                            selectedItem = selectedItem,
                            onClientClick = { selectedClientId = it },
                            onNavigateToCreateClient = { showCreateClient = true },
                            clientsRefreshKey = clientsRefreshKey,
                            onEmployeeClick = { selectedEmployeeId = it },
                            onNavigateToCreateGroup = { showCreateGroup = true },
                            groupsRefreshKey = groupsRefreshKey,
                            onNavigateToCreateEmployee = { showCreateEmployee = true },
                            employeesRefreshKey = employeesRefreshKey,
                            onNavigateToBasicSettings = { showOrgBasicSettings = true },
                            onNavigateToClientSources = { showClientSources = true },
                            onNavigateToDisciplines = { showDisciplines = true },
                            onNavigateToActivityLog = { showActivityLog = true },
                            onNavigateToChangePassword = { showChangePassword = true },
                            onNavigateToEditProfile = { showEditProfile = true },
                            onNavigateToRoles = { showRoles = true },
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Шапка аккаунта в боковой панели навигации.
 * Загружает имя и аватар через [api]; название организации и баланс — заглушки до появления соответствующих API.
 */
@Composable
private fun DrawerAccountHeader(api: ApiClient) {
    val noResponse = AuthMeResponse(UserId.new(), "", "", null)
    var me by remember { mutableStateOf(noResponse) }

    LaunchedEffect(Unit) {
        api.me().onRight { me = it }
    }

    val orgName = "ООО «Атлетика»"
    val balance = "12 400 ₽"

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
                text = orgName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.label_balance_value, balance),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Содержимое боковой панели навигации: логотип и список разделов.
 *
 * [selectedItem] — текущий выбранный пункт, [expanded] — показывать ли текстовые метки рядом с иконками,
 * [onItemSelected] — callback выбора пункта, [onToggle] — callback кнопки сворачивания (только desktop).
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
        // Логотип + кнопка сворачивания
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

        // Account header: аватар, имя пользователя, организация, баланс
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
 * [onCreateClient] вызывается при нажатии «Добавить клиента» на экране клиентов.
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
 *
 * [showMenuButton] — показывать иконку «бургер» (мобильный режим),
 * [onMenuClick] — callback кнопки открытия бокового меню, [onLogout] — callback кнопки выхода.
 * [extraActions] — контекстные действия текущего экрана, отображаются перед стандартными иконками.
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

/**
 * Область основного контента — рендерит нужный экран по [selectedItem].
 *
 * [api] — клиент API для передачи дочерним экранам,
 * [showCreateClientDialog] — управляет видимостью диалога создания клиента,
 * [onCreateClientDismiss] — callback закрытия диалога,
 * [onClientClick] — callback перехода к карточке клиента,
 * [modifier] — модификатор для применения отступов от Scaffold.
 */
@Composable
private fun ContentArea(
    api: ApiClient,
    selectedItem: NavItem,
    onClientClick: (ClientId) -> Unit = {},
    onNavigateToCreateClient: () -> Unit = {},
    clientsRefreshKey: Int = 0,
    onEmployeeClick: (EmployeeId) -> Unit = {},
    onNavigateToCreateGroup: () -> Unit = {},
    groupsRefreshKey: Int = 0,
    onNavigateToCreateEmployee: () -> Unit = {},
    employeesRefreshKey: Int = 0,
    onNavigateToBasicSettings: () -> Unit = {},
    onNavigateToClientSources: () -> Unit = {},
    onNavigateToDisciplines: () -> Unit = {},
    onNavigateToActivityLog: () -> Unit = {},
    onNavigateToChangePassword: () -> Unit = {},
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToRoles: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when (selectedItem) {
        NavItem.CLIENTS ->
            ClientsScreen(
                api = api,
                onNavigateToCreate = onNavigateToCreateClient,
                refreshKey = clientsRefreshKey,
                onClientClick = onClientClick,
                modifier = modifier,
            )
        NavItem.GROUPS ->
            GroupsScreen(
                api = api,
                onNavigateToCreate = onNavigateToCreateGroup,
                refreshKey = groupsRefreshKey,
                modifier = modifier,
            )
        NavItem.EMPLOYEES ->
            EmployeesScreen(
                api = api,
                onNavigateToCreate = onNavigateToCreateEmployee,
                refreshKey = employeesRefreshKey,
                onEmployeeClick = onEmployeeClick,
                modifier = modifier,
            )
        NavItem.SETTINGS ->
            OrgSettingsScreen(
                onNavigateToBasicSettings = onNavigateToBasicSettings,
                onNavigateToClientSources = onNavigateToClientSources,
                onNavigateToDisciplines = onNavigateToDisciplines,
                onNavigateToActivityLog = onNavigateToActivityLog,
                onNavigateToChangePassword = onNavigateToChangePassword,
                onNavigateToEditProfile = onNavigateToEditProfile,
                onNavigateToRoles = onNavigateToRoles,
                modifier = modifier,
            )
        else ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier.fillMaxSize(),
            ) {
                Text(
                    text = selectedItem.label(),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
    }
}

/** Конвертирует API-схему [ApiNotificationItem] в UI-модель [AppNotification]. */
private fun ApiNotificationItem.toAppNotification() =
    AppNotification(
        id = id,
        title = title,
        body = body,
        isRead = isRead,
        createdAt = createdAt,
        link = null, // ссылки будут добавлены позже
    )
