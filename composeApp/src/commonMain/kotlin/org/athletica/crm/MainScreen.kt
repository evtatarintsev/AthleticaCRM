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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
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
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.clients.ClientListItem
import org.athletica.crm.components.clients.ClientDetailScreen
import org.athletica.crm.components.clients.ClientsScreen
import org.athletica.crm.components.clients.ClientCreateScreen
import org.athletica.crm.components.groups.GroupCreateScreen
import org.athletica.crm.components.groups.GroupsScreen
import org.athletica.crm.components.settings.ClientSourcesScreen
import org.athletica.crm.components.settings.SportsTypesScreen
import org.athletica.crm.components.settings.OrgSettingsScreen
import org.athletica.crm.ui.WindowSize

/** Пункт бокового меню навигации. */
enum class NavItem(
    /** Иконка раздела. */
    val icon: ImageVector,
    /** Название раздела. */
    val label: String,
) {
    /** Главная страница. */
    HOME(Icons.Default.Home, "Главная"),

    /** Раздел клиентов. */
    CLIENTS(Icons.Default.Person, "Клиенты"),

    /** Раздел групп. */
    GROUPS(Icons.Default.Group, "Группы"),

    /** Расписание занятий. */
    SCHEDULE(Icons.Default.DateRange, "Расписание"),

    /** Настройки приложения. */
    SETTINGS(Icons.Default.Settings, "Настройки"),
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
    var selectedClient by remember { mutableStateOf<ClientListItem?>(null) }
    var showCreateClient by remember { mutableStateOf(false) }
    var clientsRefreshKey by remember { mutableStateOf(0) }
    var showCreateGroup by remember { mutableStateOf(false) }
    var groupsRefreshKey by remember { mutableStateOf(0) }
    var showClientSources by remember { mutableStateOf(false) }
    var showSportsTypes by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Карточка клиента накрывает весь экран поверх навигации
    if (selectedClient != null) {
        ClientDetailScreen(
            client = selectedClient!!,
            onBack = { selectedClient = null },
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

    if (showClientSources) {
        ClientSourcesScreen(onBack = { showClientSources = false })
        return
    }

    if (showSportsTypes) {
        SportsTypesScreen(onBack = { showSportsTypes = false })
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
                                onMenuClick = { scope.launch { drawerState.open() } },
                                onLogout = onLogout,
                                extraActions = { TopBarActions(selectedItem, onCreateClient = { showCreateClient = true }) },
                            )
                        },
                    ) { innerPadding ->
                        ContentArea(
                            api = api,
                            selectedItem = selectedItem,
                            onClientClick = { selectedClient = it },
                            onNavigateToCreateClient = { showCreateClient = true },
                            clientsRefreshKey = clientsRefreshKey,
                            onNavigateToCreateGroup = { showCreateGroup = true },
                            groupsRefreshKey = groupsRefreshKey,
                            onNavigateToClientSources = { showClientSources = true },
                            onNavigateToSportsTypes = { showSportsTypes = true },
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
                                    contentDescription = "Переключить навигацию",
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
                                        contentDescription = item.label,
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
                                onMenuClick = {},
                                onLogout = onLogout,
                                extraActions = { TopBarActions(selectedItem, onCreateClient = { showCreateClient = true }) },
                            )
                        },
                    ) { innerPadding ->
                        ContentArea(
                            api = api,
                            selectedItem = selectedItem,
                            onClientClick = { selectedClient = it },
                            onNavigateToCreateClient = { showCreateClient = true },
                            clientsRefreshKey = clientsRefreshKey,
                            onNavigateToCreateGroup = { showCreateGroup = true },
                            groupsRefreshKey = groupsRefreshKey,
                            onNavigateToClientSources = { showClientSources = true },
                            onNavigateToSportsTypes = { showSportsTypes = true },
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
                                onMenuClick = {},
                                onLogout = onLogout,
                                extraActions = { TopBarActions(selectedItem, onCreateClient = { showCreateClient = true }) },
                            )
                        },
                    ) { innerPadding ->
                        ContentArea(
                            api = api,
                            selectedItem = selectedItem,
                            onClientClick = { selectedClient = it },
                            onNavigateToCreateClient = { showCreateClient = true },
                            clientsRefreshKey = clientsRefreshKey,
                            onNavigateToCreateGroup = { showCreateGroup = true },
                            groupsRefreshKey = groupsRefreshKey,
                            onNavigateToClientSources = { showClientSources = true },
                            onNavigateToSportsTypes = { showSportsTypes = true },
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
 * Отображает аватар с инициалами, имя пользователя, название организации и баланс.
 * TODO: заменить заглушки на реальные данные из профиля.
 */
@Composable
private fun DrawerAccountHeader() {
    // TODO: получить из профиля / токена
    val userName = "Александр Иванов"
    val orgName = "ООО «Атлетика»"
    val balance = "12 400 ₽"
    val initials =
        userName
            .split(" ")
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")

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
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = userName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = orgName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Баланс: $balance",
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
                contentDescription = "Логотип AthleticaCRM",
                tint = MaterialTheme.colorScheme.primary,
            )
            if (expanded) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "AthleticaCRM",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Свернуть меню",
                )
            }
        }

        // Account header: аватар, имя пользователя, организация, баланс
        if (expanded) {
            DrawerAccountHeader()
        }

        HorizontalDivider()

        Spacer(Modifier.height(8.dp))

        NavItem.entries.forEach { item ->
            NavigationDrawerItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                    )
                },
                label = { Text(item.label) },
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
                    contentDescription = "Добавить клиента",
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
                placeholder = { Text("Поиск...", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(0.5f),
            )
        },
        navigationIcon = {
            if (showMenuButton) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Открыть меню",
                    )
                }
            }
        },
        actions = {
            extraActions()
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Уведомления",
                )
            }
            IconButton(onClick = onLogout) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Выйти",
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
    onClientClick: (ClientListItem) -> Unit = {},
    onNavigateToCreateClient: () -> Unit = {},
    clientsRefreshKey: Int = 0,
    onNavigateToCreateGroup: () -> Unit = {},
    groupsRefreshKey: Int = 0,
    onNavigateToClientSources: () -> Unit = {},
    onNavigateToSportsTypes: () -> Unit = {},
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
        NavItem.SETTINGS ->
            OrgSettingsScreen(
                onNavigateToClientSources = onNavigateToClientSources,
                onNavigateToSportsTypes = onNavigateToSportsTypes,
                modifier = modifier,
            )
        else ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier.fillMaxSize(),
            ) {
                Text(
                    text = selectedItem.label,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
    }
}
