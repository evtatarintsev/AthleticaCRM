package org.athletica.crm.components.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.disciplines.DisciplineDetailResponse
import org.athletica.crm.api.schemas.employees.EmployeeListItem
import org.athletica.crm.api.schemas.groups.GroupListItem
import org.athletica.crm.components.avatar.TextAvatar
import org.athletica.crm.components.settings.DisplaySettingsViewModel
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_add_group
import org.athletica.crm.generated.resources.action_delete_selected
import org.athletica.crm.generated.resources.action_notify_selected
import org.athletica.crm.generated.resources.empty_search_results
import org.athletica.crm.generated.resources.filter_chip_coaches_count
import org.athletica.crm.generated.resources.filter_chip_disciplines_count
import org.athletica.crm.generated.resources.groups_count
import org.athletica.crm.generated.resources.groups_empty
import org.athletica.crm.generated.resources.groups_load_error
import org.athletica.crm.generated.resources.groups_search_placeholder
import org.athletica.crm.generated.resources.groups_title
import org.athletica.crm.generated.resources.groups_view_all
import org.athletica.crm.generated.resources.label_coaches
import org.athletica.crm.generated.resources.label_name
import org.athletica.crm.generated.resources.label_schedule
import org.athletica.crm.generated.resources.label_selected_count
import org.athletica.crm.generated.resources.list_action_apply
import org.athletica.crm.ui.WindowSize
import org.athletica.crm.ui.list.ColumnWidth
import org.athletica.crm.ui.list.ListColumn
import org.athletica.crm.ui.list.ListData
import org.athletica.crm.ui.list.ListPageFilterPanel
import org.athletica.crm.ui.list.ListPageScaffold
import org.athletica.crm.ui.list.ListPageViewModel
import org.athletica.crm.ui.list.ListTable
import org.athletica.crm.ui.list.SavedView
import org.athletica.crm.ui.list.SelectionState
import org.athletica.crm.ui.list.SortBottomSheet
import org.athletica.crm.ui.list.SortDirection
import org.athletica.crm.ui.list.SortableColumn
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Экран списка групп организации.
 *
 * Использует [ListPageScaffold] для унифицированного UI с поиском, фильтрами по
 * дисциплине и тренеру, сортировкой и выбором строк для групповых действий.
 *
 * [api] — клиент API.
 * [displaySettingsVm] — ViewModel для сохранения настроек отображения (сортировка).
 * [windowSize] — текущий размер окна для адаптивной верстки.
 * [onNavigateToCreate] — переход к экрану создания группы.
 * [onGroupClick] — переход к деталям группы.
 * [refreshKey] — инкремент принудительно перезагружает список.
 */
@Composable
fun GroupsScreen(
    api: ApiClient,
    displaySettingsVm: DisplaySettingsViewModel,
    windowSize: WindowSize,
    onNavigateToCreate: () -> Unit = {},
    onGroupClick: (GroupId) -> Unit = {},
    refreshKey: Int = 0,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel =
        remember {
            ListPageViewModel(GroupsPageDelegate(api, displaySettingsVm), scope)
        }

    LaunchedEffect(refreshKey) { viewModel.load() }

    var selectedIds by remember { mutableStateOf<Set<GroupId>>(emptySet()) }
    var filterSheetVisible by remember { mutableStateOf(false) }
    var sortSheetVisible by remember { mutableStateOf(false) }
    var filterDraft by remember { mutableStateOf(viewModel.state.filter) }
    var availableDisciplines by remember { mutableStateOf<List<DisciplineDetailResponse>>(emptyList()) }
    var availableEmployees by remember { mutableStateOf<List<EmployeeListItem>>(emptyList()) }

    LaunchedEffect(filterSheetVisible) {
        if (filterSheetVisible) {
            filterDraft = viewModel.state.filter
            if (availableDisciplines.isEmpty()) {
                api.disciplines.list().onRight { availableDisciplines = it.disciplines }
            }
            if (availableEmployees.isEmpty()) {
                api.employees.list().onRight { availableEmployees = it.employees }
            }
        }
    }

    val title = stringResource(Res.string.groups_title)
    val allLabel = stringResource(Res.string.groups_view_all)
    val total = (viewModel.state.data as? ListData.Loaded)?.total ?: 0
    val subtitle = pluralStringResource(Res.plurals.groups_count, total, total)

    val savedViews =
        remember(allLabel) {
            listOf(
                SavedView(
                    id = GroupsSavedViews.ALL.id,
                    name = allLabel,
                    onApply = { viewModel.applySystemView(GroupsSavedViews.ALL) },
                ),
            )
        }

    val nameLabel = stringResource(Res.string.label_name)
    val scheduleLabel = stringResource(Res.string.label_schedule)
    val coachesLabel = stringResource(Res.string.label_coaches)

    val columns =
        listOf(
            ListColumn(
                id = GroupsColumns.Name,
                header = { Text(nameLabel) },
                width = ColumnWidth.Weight(1f),
                sortable = true,
                cell = { group: GroupListItem ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                        ) {
                            TextAvatar(group.name)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
            ),
            ListColumn(
                id = GroupsColumns.Schedule,
                header = { Text(scheduleLabel) },
                width = ColumnWidth.Weight(1f),
                sortable = false,
                cell = { group: GroupListItem ->
                    Text(
                        text = formatScheduleSummary(group),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            ),
            ListColumn(
                id = GroupsColumns.Coaches,
                header = { Text(coachesLabel) },
                width = ColumnWidth.Weight(1f),
                sortable = true,
                cell = { group: GroupListItem ->
                    Text(
                        text = group.employees.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            ),
        )

    val sortableColumns =
        listOf(
            SortableColumn(GroupsColumns.Name, nameLabel),
            SortableColumn(GroupsColumns.Coaches, coachesLabel),
        )

    val sortChipLabel =
        viewModel.state.sort?.let { s ->
            val dir = if (s.direction == SortDirection.Asc) "↑" else "↓"
            val colName =
                when (s.columnId) {
                    GroupsColumns.Name -> nameLabel
                    GroupsColumns.Coaches -> coachesLabel
                    else -> s.columnId.value
                }
            "$colName $dir"
        }

    val visible = viewModel.visible
    val selectAllState =
        when {
            selectedIds.isEmpty() -> ToggleableState.Off
            selectedIds.containsAll(visible.map { it.id }) -> ToggleableState.On
            else -> ToggleableState.Indeterminate
        }

    val selection =
        SelectionState(
            isSelected = { group: GroupListItem -> group.id in selectedIds },
            onToggle = { group: GroupListItem ->
                selectedIds =
                    if (group.id in selectedIds) {
                        selectedIds - group.id
                    } else {
                        selectedIds + group.id
                    }
            },
            onToggleAll = {
                selectedIds =
                    if (selectAllState == ToggleableState.On) emptySet() else visible.map { it.id }.toSet()
            },
            selectAllState = selectAllState,
        )

    val applyLabel = stringResource(Res.string.list_action_apply)

    ListPageScaffold(
        title = title,
        subtitle = subtitle,
        windowSize = windowSize,
        searchQuery = viewModel.state.searchQuery,
        onSearchQueryChange = viewModel::setSearch,
        searchPlaceholder = stringResource(Res.string.groups_search_placeholder),
        savedViews = savedViews,
        activeSavedViewId = viewModel.state.activeSavedViewId,
        onSaveCurrentView = null,
        activeFilterCount = viewModel.state.filter.activeCount,
        onOpenFilters = { filterSheetVisible = true },
        quickFilterChips = {
            val filter = viewModel.state.filter
            if (filter.disciplineIds.isNotEmpty()) {
                FilterChip(
                    selected = true,
                    onClick = { viewModel.setFilter(filter.copy(disciplineIds = emptySet())) },
                    label = {
                        Text(stringResource(Res.string.filter_chip_disciplines_count, filter.disciplineIds.size))
                    },
                    trailingIcon = {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            if (filter.employeeIds.isNotEmpty()) {
                FilterChip(
                    selected = true,
                    onClick = { viewModel.setFilter(filter.copy(employeeIds = emptySet())) },
                    label = {
                        Text(stringResource(Res.string.filter_chip_coaches_count, filter.employeeIds.size))
                    },
                    trailingIcon = {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        },
        filterPanel =
            ListPageFilterPanel(
                visible = filterSheetVisible,
                onDismiss = { filterSheetVisible = false },
                onReset = { filterDraft = GroupsFilterState() },
                onSaveAsView = null,
                onApply = {
                    viewModel.setFilter(filterDraft)
                    filterSheetVisible = false
                },
                applyEnabled = true,
                applyLabel = applyLabel,
                content = {
                    GroupsFilterContent(
                        draft = filterDraft,
                        onDraftChange = { filterDraft = it },
                        disciplines = availableDisciplines,
                        employees = availableEmployees,
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
            if (selectedIds.isEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onNavigateToCreate,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(Res.string.action_add_group)) },
                )
            }
        },
        bulkActionBar =
            if (selectedIds.isNotEmpty()) {
                {
                    GroupsBulkActionBar(
                        selectedCount = selectedIds.size,
                        onDelete = { selectedIds = emptySet() },
                        onNotify = {},
                    )
                }
            } else {
                null
            },
        content = {
            GroupsContent(
                viewModel = viewModel,
                columns = columns,
                windowSize = windowSize,
                selection = selection,
                onGroupClick = onGroupClick,
            )
        },
        modifier = modifier,
    )

    if (sortSheetVisible) {
        SortBottomSheet(
            columns = sortableColumns,
            current = viewModel.state.sort,
            onSortChange = { newSort ->
                viewModel.applySort(newSort)
                sortSheetVisible = false
            },
            onDismiss = { sortSheetVisible = false },
        )
    }
}

/** Основное содержимое списка групп с адаптацией под состояния загрузки и пустого списка. */
@Composable
private fun GroupsContent(
    viewModel: ListPageViewModel<GroupListItem, GroupsFilterState>,
    columns: List<ListColumn<GroupListItem>>,
    windowSize: WindowSize,
    selection: SelectionState<GroupListItem>,
    onGroupClick: (GroupId) -> Unit,
) {
    when (viewModel.state.data) {
        is ListData.Loading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        is ListData.Error ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(Res.string.groups_load_error),
                    color = MaterialTheme.colorScheme.error,
                )
            }

        is ListData.Loaded -> {
            val emptyRes =
                if (viewModel.state.searchQuery.isNotBlank() || viewModel.state.filter.activeCount > 0) {
                    Res.string.empty_search_results
                } else {
                    Res.string.groups_empty
                }
            if (viewModel.visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(emptyRes), style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                if (windowSize == WindowSize.COMPACT) {
                    LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                        items(viewModel.visible, key = { it.id.value }) { group ->
                            GroupMobileItem(
                                group = group,
                                isSelected = selection.isSelected(group),
                                onToggleSelect = { selection.onToggle(group) },
                                onClick = { onGroupClick(group.id) },
                            )
                        }
                    }
                } else {
                    ListTable(
                        items = viewModel.visible,
                        columns = columns,
                        rowKey = { it.id.value },
                        onRowClick = { onGroupClick(it.id) },
                        mobileItem = { group, isSelected, onToggleSelect ->
                            GroupMobileItem(
                                group = group,
                                isSelected = isSelected,
                                onToggleSelect = onToggleSelect,
                                onClick = { onGroupClick(group.id) },
                            )
                        },
                        windowSize = windowSize,
                        selection = selection,
                        sort = viewModel.state.sort,
                        onSortChange = { columnId -> viewModel.cycleSort(columnId) },
                    )
                }
            }
        }
    }
}

/**
 * Нижняя панель групповых действий, появляющаяся при выборе групп.
 * [selectedCount] — количество выбранных записей.
 */
@Composable
private fun GroupsBulkActionBar(
    selectedCount: Int,
    onDelete: () -> Unit,
    onNotify: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BottomAppBar(modifier = modifier) {
        Text(
            text = stringResource(Res.string.label_selected_count, selectedCount),
            style = MaterialTheme.typography.titleSmall,
            modifier =
                Modifier
                    .padding(start = 16.dp)
                    .weight(1f),
        )
        IconButton(onClick = onDelete) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = stringResource(Res.string.action_delete_selected))
        }
        IconButton(onClick = onNotify) {
            Icon(imageVector = Icons.Default.Notifications, contentDescription = stringResource(Res.string.action_notify_selected))
        }
    }
}
