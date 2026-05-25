package org.athletica.crm.components.employees

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.employees.EmployeeListItem
import org.athletica.crm.components.settings.DisplaySettingsViewModel
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_add_employee
import org.athletica.crm.generated.resources.action_deactivate_selected
import org.athletica.crm.generated.resources.action_export_selected
import org.athletica.crm.generated.resources.action_notify_selected_employees
import org.athletica.crm.generated.resources.cd_send_access
import org.athletica.crm.generated.resources.employees_col_contact
import org.athletica.crm.generated.resources.employees_col_roles
import org.athletica.crm.generated.resources.employees_empty
import org.athletica.crm.generated.resources.employees_filter_only_active
import org.athletica.crm.generated.resources.employees_search_placeholder
import org.athletica.crm.generated.resources.employees_subtitle_count
import org.athletica.crm.generated.resources.employees_view_active
import org.athletica.crm.generated.resources.employees_view_all
import org.athletica.crm.generated.resources.empty_search_results
import org.athletica.crm.generated.resources.label_employee_status
import org.athletica.crm.generated.resources.label_person_name
import org.athletica.crm.generated.resources.label_selected_count
import org.athletica.crm.generated.resources.list_action_apply
import org.athletica.crm.generated.resources.screen_employees
import org.athletica.crm.ui.WindowSize
import org.athletica.crm.ui.list.ColumnWidth
import org.athletica.crm.ui.list.ListColumn
import org.athletica.crm.ui.list.ListData
import org.athletica.crm.ui.list.ListPageFilterPanel
import org.athletica.crm.ui.list.ListPageScaffold
import org.athletica.crm.ui.list.ListPageViewModel
import org.athletica.crm.ui.list.ListTable
import org.athletica.crm.ui.list.SavedView
import org.athletica.crm.ui.list.SavedViewId
import org.athletica.crm.ui.list.SelectionState
import org.athletica.crm.ui.list.SortBottomSheet
import org.athletica.crm.ui.list.SortDirection
import org.athletica.crm.ui.list.SortableColumn
import org.jetbrains.compose.resources.stringResource

/**
 * Экран списка сотрудников.
 * Использует [ListPageScaffold] для унифицированного UI с поиском, фильтрами и сохранёнными видами.
 *
 * [api] — клиент API.
 * [displaySettingsVm] — ViewModel для сохранения настроек отображения.
 * [windowSize] — текущий размер окна для адаптивной верстки.
 * [onNavigateToCreate] — переход к созданию сотрудника.
 * [onEmployeeClick] — переход к деталям сотрудника.
 * [refreshKey] — инкремент принудительно перезагружает список.
 */
@Composable
fun EmployeesScreen(
    api: ApiClient,
    displaySettingsVm: DisplaySettingsViewModel,
    windowSize: WindowSize,
    onNavigateToCreate: () -> Unit = {},
    onEmployeeClick: (EmployeeId) -> Unit = {},
    refreshKey: Int = 0,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel =
        remember {
            ListPageViewModel(EmployeesPageDelegate(api, displaySettingsVm), scope)
        }

    LaunchedEffect(refreshKey) { viewModel.load() }

    var filterSheetVisible by remember { mutableStateOf(false) }
    var sortSheetVisible by remember { mutableStateOf(false) }
    var filterDraft by remember { mutableStateOf(viewModel.state.filter) }
    var selectedIds by remember { mutableStateOf<Set<EmployeeId>>(emptySet()) }
    var showSendAccessFor by remember { mutableStateOf<EmployeeListItem?>(null) }

    LaunchedEffect(filterSheetVisible) {
        if (filterSheetVisible) {
            filterDraft = viewModel.state.filter
        }
    }

    showSendAccessFor?.let { emp ->
        SendAccessDialog(
            api = api,
            employeeId = emp.id,
            defaultEmail = emp.email,
            onSuccess = {
                showSendAccessFor = null
                selectedIds = emptySet()
                viewModel.load()
            },
            onDismiss = { showSendAccessFor = null },
        )
    }

    val title = stringResource(Res.string.screen_employees)
    val allLabel = stringResource(Res.string.employees_view_all)
    val activeLabel = stringResource(Res.string.employees_view_active)
    val total = (viewModel.state.data as? ListData.Loaded)?.total ?: 0
    val subtitleTemplate = stringResource(Res.string.employees_subtitle_count, total)
    val subtitle = computeSubtitle(subtitleTemplate, viewModel.state.activeSavedViewId, activeLabel)

    val savedViews =
        remember(allLabel, activeLabel) {
            listOf(
                SavedView(
                    id = EmployeesSavedViews.ALL.id,
                    name = allLabel,
                    onApply = { viewModel.applySystemView(EmployeesSavedViews.ALL) },
                ),
                SavedView(
                    id = EmployeesSavedViews.ACTIVE.id,
                    name = activeLabel,
                    onApply = { viewModel.applySystemView(EmployeesSavedViews.ACTIVE) },
                ),
            )
        }

    val nameCol = stringResource(Res.string.label_person_name)
    val statusCol = stringResource(Res.string.label_employee_status)
    val rolesCol = stringResource(Res.string.employees_col_roles)
    val contactCol = stringResource(Res.string.employees_col_contact)

    val columns = employeeColumns(api, nameCol, statusCol, rolesCol, contactCol)

    val sortChipLabel =
        viewModel.state.sort?.let { s ->
            val dir = if (s.direction == SortDirection.Asc) "↑" else "↓"
            val colName =
                when (s.columnId) {
                    EmployeesColumns.Name -> nameCol
                    EmployeesColumns.Active -> statusCol
                    else -> s.columnId.value
                }
            "$colName $dir"
        }

    val applyLabel = stringResource(Res.string.list_action_apply)

    ListPageScaffold(
        title = title,
        subtitle = subtitle,
        windowSize = windowSize,
        searchQuery = viewModel.state.searchQuery,
        onSearchQueryChange = viewModel::setSearch,
        searchPlaceholder = stringResource(Res.string.employees_search_placeholder),
        savedViews = savedViews,
        activeSavedViewId = viewModel.state.activeSavedViewId,
        onSaveCurrentView = null,
        activeFilterCount = viewModel.state.filter.activeCount,
        onOpenFilters = { filterSheetVisible = true },
        quickFilterChips = {
            if (viewModel.state.filter.onlyActive) {
                FilterChip(
                    selected = true,
                    onClick = {
                        viewModel.setFilter(viewModel.state.filter.copy(onlyActive = false))
                    },
                    label = { Text(stringResource(Res.string.employees_filter_only_active)) },
                    trailingIcon = {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        },
        filterPanel =
            ListPageFilterPanel(
                visible = filterSheetVisible,
                onDismiss = { filterSheetVisible = false },
                onReset = { filterDraft = EmployeesFilter() },
                onSaveAsView = null,
                onApply = {
                    viewModel.setFilter(filterDraft)
                    filterSheetVisible = false
                },
                applyEnabled = true,
                applyLabel = applyLabel,
                content = {
                    EmployeesFilterContent(
                        draft = filterDraft,
                        onDraftChange = { filterDraft = it },
                    )
                },
            ),
        onOpenColumnSettings = null,
        sortChipLabel = sortChipLabel,
        onOpenSortDialog =
            if (windowSize == WindowSize.COMPACT) {
                { sortSheetVisible = true }
            } else {
                null
            },
        fab = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToCreate,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(Res.string.action_add_employee)) },
            )
        },
        bulkActionBar =
            if (selectedIds.isNotEmpty()) {
                {
                    val sendAccessTarget: EmployeeListItem? =
                        if (selectedIds.size == 1) {
                            val id = selectedIds.first()
                            viewModel.visible.find { it.id == id }?.takeIf { !it.isActive }
                        } else {
                            null
                        }
                    EmployeesBottomActionBar(
                        selectedCount = selectedIds.size,
                        sendAccessEnabled = sendAccessTarget != null,
                        onSendAccess = { showSendAccessFor = sendAccessTarget },
                        onNotify = {},
                        onDeactivate = {},
                        onExport = {},
                    )
                }
            } else {
                null
            },
        content = {
            EmployeesContent(
                viewModel = viewModel,
                columns = columns,
                windowSize = windowSize,
                api = api,
                selectedIds = selectedIds,
                onSelectionChange = { selectedIds = it },
                onEmployeeClick = onEmployeeClick,
            )
        },
        modifier = modifier,
    )

    if (sortSheetVisible) {
        SortBottomSheet(
            columns =
                listOf(
                    SortableColumn(EmployeesColumns.Name, nameCol),
                    SortableColumn(EmployeesColumns.Active, statusCol),
                ),
            current = viewModel.state.sort,
            onSortChange = { newSort ->
                viewModel.applySort(newSort)
                sortSheetVisible = false
            },
            onDismiss = { sortSheetVisible = false },
        )
    }
}

/** Вычисляет подзаголовок топбара: количество сотрудников и опционально имя активного вида. */
@Composable
private fun computeSubtitle(
    countText: String,
    activeSavedViewId: SavedViewId?,
    activeLabel: String,
): String? {
    val viewName =
        when (activeSavedViewId) {
            EmployeesSavedViews.ACTIVE.id -> activeLabel
            EmployeesSavedViews.ALL.id -> null
            else -> null
        }
    return if (viewName != null) "$countText · $viewName" else countText
}

/** Декларации колонок таблицы сотрудников. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun employeeColumns(
    api: ApiClient,
    nameCol: String,
    statusCol: String,
    rolesCol: String,
    contactCol: String,
): List<ListColumn<EmployeeListItem>> =
    listOf(
        ListColumn(
            id = EmployeesColumns.Name,
            header = { Text(nameCol) },
            width = ColumnWidth.Weight(1f),
            sortable = true,
            cell = { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmployeeAvatar(employee = item, api = api)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = item.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
        ),
        ListColumn(
            id = EmployeesColumns.Active,
            header = { Text(statusCol) },
            width = ColumnWidth.Fixed(120.dp),
            sortable = true,
            cell = { item -> EmployeeStatusBadge(isActive = item.isActive) },
        ),
        ListColumn(
            id = EmployeesColumns.Roles,
            header = { Text(rolesCol) },
            width = ColumnWidth.Fixed(220.dp),
            sortable = false,
            cell = { item ->
                if (item.roles.isEmpty()) {
                    Text("—", maxLines = 1)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        item.roles.forEach { role ->
                            AssistChip(onClick = {}, label = { Text(role.name) })
                        }
                    }
                }
            },
        ),
        ListColumn(
            id = EmployeesColumns.Contact,
            header = { Text(contactCol) },
            width = ColumnWidth.Fixed(200.dp),
            sortable = false,
            cell = { item ->
                Text(
                    text = item.email ?: item.phoneNo ?: "—",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        ),
    )

/** Основное содержимое списка сотрудников с адаптацией под состояния загрузки и пустого списка. */
@Composable
private fun EmployeesContent(
    viewModel: ListPageViewModel<EmployeeListItem, EmployeesFilter>,
    columns: List<ListColumn<EmployeeListItem>>,
    windowSize: WindowSize,
    api: ApiClient,
    selectedIds: Set<EmployeeId>,
    onSelectionChange: (Set<EmployeeId>) -> Unit,
    onEmployeeClick: (EmployeeId) -> Unit,
) {
    when (val data = viewModel.state.data) {
        is ListData.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is ListData.Error -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = errorMessage(data.error),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        is ListData.Loaded -> {
            if (viewModel.visible.isEmpty()) {
                val emptyRes =
                    if (viewModel.state.searchQuery.isNotBlank() || viewModel.state.filter.activeCount > 0) {
                        Res.string.empty_search_results
                    } else {
                        Res.string.employees_empty
                    }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(emptyRes),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                val visibleItems = viewModel.visible
                val selectAllState =
                    when {
                        selectedIds.isEmpty() -> ToggleableState.Off
                        selectedIds.containsAll(visibleItems.map { it.id }) -> ToggleableState.On
                        else -> ToggleableState.Indeterminate
                    }
                val selection =
                    SelectionState<EmployeeListItem>(
                        isSelected = { it.id in selectedIds },
                        onToggle = { item ->
                            onSelectionChange(
                                if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id,
                            )
                        },
                        onToggleAll = {
                            onSelectionChange(
                                if (selectAllState == ToggleableState.On) {
                                    emptySet()
                                } else {
                                    visibleItems.map { it.id }.toSet()
                                },
                            )
                        },
                        selectAllState = selectAllState,
                    )

                ListTable(
                    items = visibleItems,
                    columns = columns,
                    rowKey = { it.id },
                    onRowClick = { onEmployeeClick(it.id) },
                    mobileItem = { item, isSelected, onToggleSelect ->
                        EmployeeRow(
                            employee = item,
                            api = api,
                            selected = isSelected,
                            onCheckedChange = { onToggleSelect() },
                            onClick = { onEmployeeClick(item.id) },
                        )
                    },
                    windowSize = windowSize,
                    selection = selection,
                    sort = viewModel.state.sort,
                    onSortChange = { columnId -> viewModel.cycleSort(columnId) },
                    modifier = Modifier.padding(bottom = if (windowSize == WindowSize.COMPACT) 80.dp else 0.dp),
                )
            }
        }
    }
}

@Composable
private fun errorMessage(error: ApiClientError): String = error.toEmployeesApiError().message()

@Composable
private fun EmployeesBottomActionBar(
    selectedCount: Int,
    sendAccessEnabled: Boolean,
    onSendAccess: () -> Unit,
    onNotify: () -> Unit,
    onDeactivate: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BottomAppBar(modifier = modifier) {
        Text(
            text = stringResource(Res.string.label_selected_count, selectedCount),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp).weight(1f),
        )
        IconButton(onClick = onSendAccess, enabled = sendAccessEnabled) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(Res.string.cd_send_access),
            )
        }
        IconButton(onClick = onNotify) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = stringResource(Res.string.action_notify_selected_employees),
            )
        }
        IconButton(onClick = onDeactivate) {
            Icon(
                imageVector = Icons.Default.PersonOff,
                contentDescription = stringResource(Res.string.action_deactivate_selected),
            )
        }
        IconButton(onClick = onExport) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = stringResource(Res.string.action_export_selected),
            )
        }
    }
}
