package org.athletica.crm

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExitToApp
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient

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
 * @param api клиент API для передачи дочерним экранам
 * @param onLogout вызывается при нажатии кнопки выхода
 */
@Composable
fun MainScreen(api: ApiClient, onLogout: () -> Unit = {}) {
    var selectedItem by remember { mutableStateOf(NavItem.HOME) }
    var isSidebarExpanded by remember { mutableStateOf(true) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        when {
            maxWidth < 600.dp -> {
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
                            )
                        },
                    ) { innerPadding ->
                        ContentArea(
                            selectedItem = selectedItem,
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }

            maxWidth < 1200.dp || !isSidebarExpanded -> {
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
                            )
                        },
                    ) { innerPadding ->
                        ContentArea(
                            selectedItem = selectedItem,
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
                            )
                        },
                    ) { innerPadding ->
                        ContentArea(
                            selectedItem = selectedItem,
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Содержимое боковой панели навигации: логотип и список разделов.
 *
 * @param selectedItem текущий выбранный пункт меню
 * @param expanded true — показывать текстовые метки рядом с иконками
 * @param onItemSelected обработчик выбора пункта меню
 * @param onToggle обработчик нажатия кнопки сворачивания (только desktop)
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
 * Верхняя панель приложения с поиском, уведомлениями и кнопкой открытия меню.
 *
 * @param showMenuButton true — показывать иконку «бургер» (мобильный режим)
 * @param onMenuClick обработчик нажатия кнопки открытия бокового меню
 * @param onLogout обработчик нажатия кнопки выхода
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopAppBar(
    showMenuButton: Boolean,
    onMenuClick: () -> Unit,
    onLogout: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }

    TopAppBar(
        title = {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Поиск...") },
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
 * Область основного контента — слот для страниц приложения.
 * TODO: заменить на маршрутизацию реальных страниц по [selectedItem]
 *
 * @param selectedItem текущий выбранный раздел навигации
 * @param modifier модификатор для применения отступов от Scaffold
 */
@Composable
private fun ContentArea(
    selectedItem: NavItem,
    modifier: Modifier = Modifier,
) {
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
