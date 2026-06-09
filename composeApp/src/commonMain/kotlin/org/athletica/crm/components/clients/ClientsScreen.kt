package org.athletica.crm.components.clients

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.clients.ClientField
import org.athletica.crm.api.schemas.clients.ClientListItem
import org.athletica.crm.api.schemas.clients.contactsOfType
import org.athletica.crm.api.schemas.clients.field
import org.athletica.crm.components.avatar.Avatar
import org.athletica.crm.components.settings.DisplaySettingsViewModel
import org.athletica.crm.core.contacts.ContactType
import org.athletica.crm.core.customfields.CustomFieldDefinition
import org.athletica.crm.core.customfields.CustomFieldValue
import org.athletica.crm.core.customfields.displayValue
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.money.formatted
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_add_client
import org.athletica.crm.generated.resources.action_add_client_group
import org.athletica.crm.generated.resources.action_delete_selected
import org.athletica.crm.generated.resources.action_export_selected
import org.athletica.crm.generated.resources.action_notify_selected
import org.athletica.crm.generated.resources.clients_count
import org.athletica.crm.generated.resources.clients_empty
import org.athletica.crm.generated.resources.clients_load_error
import org.athletica.crm.generated.resources.clients_search_placeholder
import org.athletica.crm.generated.resources.clients_title
import org.athletica.crm.generated.resources.clients_view_all
import org.athletica.crm.generated.resources.clients_view_in_debt
import org.athletica.crm.generated.resources.clients_view_without_group
import org.athletica.crm.generated.resources.empty_search_results
import org.athletica.crm.generated.resources.filter_chip_has_debt
import org.athletica.crm.generated.resources.filter_gender_female
import org.athletica.crm.generated.resources.filter_gender_male
import org.athletica.crm.generated.resources.filter_no_group
import org.athletica.crm.generated.resources.label_balance
import org.athletica.crm.generated.resources.label_birthday
import org.athletica.crm.generated.resources.label_gender
import org.athletica.crm.generated.resources.label_groups
import org.athletica.crm.generated.resources.label_person_name
import org.athletica.crm.generated.resources.label_selected_count
import org.athletica.crm.generated.resources.list_action_apply
import org.athletica.crm.ui.WindowSize
import org.athletica.crm.ui.list.ColumnId
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
 * Экран списка клиентов.
 * Использует [ListPageScaffold] для унифицированного UI с поиском, фильтрами,
 * сохранёнными видами и выбором строк для групповых действий.
 *
 * [api] — клиент API.
 * [displaySettingsVm] — ViewModel для сохранения настроек отображения.
 * [windowSize] — текущий размер окна для адаптивной верстки.
 * [onNavigateToCreate] — переход к созданию клиента.
 * [onNavigateToExport] — переход к экрану экспорта с выбранными ID.
 * [refreshKey] — инкремент принудительно перезагружает список.
 * [onClientClick] — переход к карточке клиента.
 */
@Composable
fun ClientsScreen(
    api: ApiClient,
    displaySettingsVm: DisplaySettingsViewModel,
    windowSize: WindowSize,
    onNavigateToCreate: () -> Unit = {},
    onNavigateToExport: (List<ClientId>) -> Unit = {},
    refreshKey: Int = 0,
    onClientClick: (ClientId) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel =
        remember {
            ListPageViewModel(ClientsPageDelegate(api, displaySettingsVm), scope)
        }

    LaunchedEffect(refreshKey) { viewModel.load() }

    var availableCustomFields by remember { mutableStateOf<List<CustomFieldDefinition>>(emptyList()) }
    LaunchedEffect(Unit) {
        api.customFields.list("CLIENT").onRight { availableCustomFields = it }
    }

    val clientSettings =
        displaySettingsVm.displaySettings.clients.toDisplaySettings(availableCustomFields)

    var selectedIds by remember { mutableStateOf<Set<ClientId>>(emptySet()) }
    var filterSheetVisible by remember { mutableStateOf(false) }
    var sortSheetVisible by remember { mutableStateOf(false) }
    var showAddToGroupSheet by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var filterDraft by remember { mutableStateOf(viewModel.state.filter) }

    LaunchedEffect(filterSheetVisible) {
        if (filterSheetVisible) {
            filterDraft = viewModel.state.filter
        }
    }

    if (showSettingsDialog) {
        ClientsSettingsDialog(
            settings = clientSettings,
            availableCustomFields = availableCustomFields,
            onSettingsChange = { newSettings ->
                displaySettingsVm.update(
                    displaySettingsVm.displaySettings.copy(
                        clients = newSettings.toApiModel(),
                    ),
                )
            },
            onDismiss = { showSettingsDialog = false },
        )
    }

    val title = stringResource(Res.string.clients_title)
    val allLabel = stringResource(Res.string.clients_view_all)
    val inDebtLabel = stringResource(Res.string.clients_view_in_debt)
    val withoutGroupLabel = stringResource(Res.string.clients_view_without_group)

    val total = (viewModel.state.data as? ListData.Loaded)?.total ?: 0
    val subtitle = pluralStringResource(Res.plurals.clients_count, total, total)

    val savedViews =
        remember(allLabel, inDebtLabel, withoutGroupLabel) {
            listOf(
                SavedView(
                    id = ClientsSavedViews.ALL.id,
                    name = allLabel,
                    onApply = { viewModel.applySystemView(ClientsSavedViews.ALL) },
                ),
                SavedView(
                    id = ClientsSavedViews.IN_DEBT.id,
                    name = inDebtLabel,
                    onApply = { viewModel.applySystemView(ClientsSavedViews.IN_DEBT) },
                ),
                SavedView(
                    id = ClientsSavedViews.WITHOUT_GROUP.id,
                    name = withoutGroupLabel,
                    onApply = { viewModel.applySystemView(ClientsSavedViews.WITHOUT_GROUP) },
                ),
            )
        }

    val nameLabel = stringResource(Res.string.label_person_name)
    val balanceLabel = stringResource(Res.string.label_balance)
    val birthdayLabel = stringResource(Res.string.label_birthday)
    val genderLabel = stringResource(Res.string.label_gender)
    val groupsLabel = stringResource(Res.string.label_groups)

    val columns =
        buildList {
            add(
                ListColumn(
                    id = ClientsColumns.Name,
                    header = { Text(nameLabel) },
                    width = ColumnWidth.Weight(1f),
                    sortable = true,
                    cell = { client: ClientListItem ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier =
                                    Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                            ) {
                                Avatar(client.avatarId, client.name, api)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = client.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                ),
            )
            clientSettings.columns.forEach { column ->
                when (column) {
                    is ClientColumn.Standard ->
                        add(
                            ListColumn(
                                id = ColumnId(column.apiKey),
                                header = {
                                    Text(
                                        text =
                                            when (column.clientField) {
                                                ClientField.GENDER -> genderLabel
                                                ClientField.BIRTHDAY -> birthdayLabel
                                                ClientField.BALANCE -> balanceLabel
                                                ClientField.GROUPS -> groupsLabel
                                            },
                                        textAlign =
                                            if (column.clientField == ClientField.BALANCE) {
                                                TextAlign.End
                                            } else {
                                                TextAlign.Center
                                            },
                                    )
                                },
                                width = ColumnWidth.Fixed(column.width),
                                sortable = column.clientField in listOf(ClientField.BIRTHDAY, ClientField.BALANCE),
                                cell = { client: ClientListItem ->
                                    StandardColumnCell(column.clientField, client)
                                },
                            ),
                        )
                    is ClientColumn.Custom ->
                        add(
                            ListColumn(
                                id = ColumnId(column.apiKey),
                                header = {
                                    Text(column.label, textAlign = TextAlign.Center)
                                },
                                width = ColumnWidth.Fixed(column.width),
                                sortable = false,
                                cell = { client: ClientListItem ->
                                    CustomFieldCell(client.field(column.apiKey))
                                },
                            ),
                        )
                    is ClientColumn.Contact ->
                        add(
                            ListColumn(
                                id = ColumnId(column.apiKey),
                                header = {
                                    Text(stringResource(column.type.labelRes()), textAlign = TextAlign.Center)
                                },
                                width = ColumnWidth.Fixed(column.width),
                                sortable = false,
                                cell = { client: ClientListItem ->
                                    ContactColumnCell(column.type, client)
                                },
                            ),
                        )
                }
            }
        }

    val sortableColumns =
        listOf(
            SortableColumn(ClientsColumns.Name, nameLabel),
            SortableColumn(ClientsColumns.Balance, balanceLabel),
            SortableColumn(ClientsColumns.Birthday, birthdayLabel),
        )

    val sortChipLabel =
        viewModel.state.sort?.let { s ->
            val dir = if (s.direction == SortDirection.Asc) "↑" else "↓"
            val colName =
                when (s.columnId) {
                    ClientsColumns.Name -> nameLabel
                    ClientsColumns.Balance -> balanceLabel
                    ClientsColumns.Birthday -> birthdayLabel
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
            isSelected = { client: ClientListItem -> client.id in selectedIds },
            onToggle = { client: ClientListItem ->
                selectedIds =
                    if (client.id in selectedIds) {
                        selectedIds - client.id
                    } else {
                        selectedIds + client.id
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
        searchPlaceholder = stringResource(Res.string.clients_search_placeholder),
        savedViews = savedViews,
        activeSavedViewId = viewModel.state.activeSavedViewId,
        onSaveCurrentView = null,
        activeFilterCount = viewModel.state.filter.activeCount,
        onOpenFilters = { filterSheetVisible = true },
        quickFilterChips = {
            val filter = viewModel.state.filter
            if (filter.gender != GenderFilter.All) {
                FilterChip(
                    selected = true,
                    elevation = FilterChipDefaults.filterChipElevation(hoveredElevation = 0.dp),
                    onClick = { viewModel.setFilter(filter.copy(gender = GenderFilter.All)) },
                    label = {
                        Text(
                            when (filter.gender) {
                                GenderFilter.Male -> stringResource(Res.string.filter_gender_male)
                                GenderFilter.Female -> stringResource(Res.string.filter_gender_female)
                                GenderFilter.All -> ""
                            },
                        )
                    },
                    trailingIcon = {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            if (filter.hasDebtOnly) {
                FilterChip(
                    selected = true,
                    elevation = FilterChipDefaults.filterChipElevation(hoveredElevation = 0.dp),
                    onClick = { viewModel.setFilter(filter.copy(hasDebtOnly = false)) },
                    label = { Text(stringResource(Res.string.filter_chip_has_debt)) },
                    trailingIcon = {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            if (filter.noGroupOnly) {
                FilterChip(
                    selected = true,
                    elevation = FilterChipDefaults.filterChipElevation(hoveredElevation = 0.dp),
                    onClick = { viewModel.setFilter(filter.copy(noGroupOnly = false)) },
                    label = { Text(stringResource(Res.string.filter_no_group)) },
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
                onReset = { filterDraft = ClientFilterState() },
                onSaveAsView = null,
                onApply = {
                    viewModel.setFilter(filterDraft)
                    filterSheetVisible = false
                },
                applyEnabled = true,
                applyLabel = applyLabel,
                content = {
                    ClientsFilterContent(
                        draft = filterDraft,
                        onDraftChange = { filterDraft = it },
                    )
                },
            ),
        onOpenColumnSettings = { showSettingsDialog = true },
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
                    text = { Text(stringResource(Res.string.action_add_client)) },
                )
            }
        },
        bulkActionBar =
            if (selectedIds.isNotEmpty()) {
                {
                    ClientsBulkActionBar(
                        selectedCount = selectedIds.size,
                        onAddToGroup = { showAddToGroupSheet = true },
                        onDelete = { selectedIds = emptySet() },
                        onNotify = {},
                        onExport = { onNavigateToExport(selectedIds.toList()) },
                    )
                }
            } else {
                null
            },
        content = {
            ClientsContent(
                viewModel = viewModel,
                columns = columns,
                windowSize = windowSize,
                api = api,
                selection = selection,
                onClientClick = onClientClick,
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

    if (showAddToGroupSheet && selectedIds.isNotEmpty()) {
        AddToGroupSheet(
            clientIds = selectedIds.toList(),
            api = api,
            onDismiss = { showAddToGroupSheet = false },
            onGroupAdded = {
                showAddToGroupSheet = false
                selectedIds = emptySet()
            },
        )
    }
}

/** Основное содержимое списка клиентов с адаптацией под состояния загрузки и пустого списка. */
@Composable
private fun ClientsContent(
    viewModel: ListPageViewModel<ClientListItem, ClientFilterState>,
    columns: List<ListColumn<ClientListItem>>,
    windowSize: WindowSize,
    api: ApiClient,
    selection: SelectionState<ClientListItem>,
    onClientClick: (ClientId) -> Unit,
) {
    when (viewModel.state.data) {
        is ListData.Loading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        is ListData.Error ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(Res.string.clients_load_error),
                    color = MaterialTheme.colorScheme.error,
                )
            }

        is ListData.Loaded -> {
            val emptyRes =
                if (viewModel.state.searchQuery.isNotBlank() || viewModel.state.filter.activeCount > 0) {
                    Res.string.empty_search_results
                } else {
                    Res.string.clients_empty
                }
            if (viewModel.visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(emptyRes), style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                if (windowSize == WindowSize.COMPACT) {
                    LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                        items(viewModel.visible, key = { it.id.value }) { client ->
                            ClientMobileItem(
                                client = client,
                                api = api,
                                isSelected = selection.isSelected(client),
                                onToggleSelect = { selection.onToggle(client) },
                                onClick = { onClientClick(client.id) },
                            )
                        }
                    }
                } else {
                    ListTable(
                        items = viewModel.visible,
                        columns = columns,
                        rowKey = { it.id.value },
                        onRowClick = { onClientClick(it.id) },
                        mobileItem = { client, isSelected, onToggleSelect ->
                            ClientMobileItem(
                                client = client,
                                api = api,
                                isSelected = isSelected,
                                onToggleSelect = onToggleSelect,
                                onClick = { onClientClick(client.id) },
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

/** Ячейка стандартного поля клиента в таблице. */
@Composable
private fun StandardColumnCell(
    field: ClientField,
    client: ClientListItem,
) {
    when (field) {
        ClientField.GENDER ->
            Text(
                text = client.gender.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        ClientField.BIRTHDAY ->
            Text(
                text = client.birthday?.toString() ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        ClientField.BALANCE ->
            Text(
                text = client.balance.formatted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                color =
                    if (client.balance.isNegative) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
        ClientField.GROUPS ->
            Text(
                text = client.groups.joinToString(", ") { it.name },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
    }
}

/** Ячейка кастомного поля клиента в таблице. */
@Composable
private fun CustomFieldCell(value: CustomFieldValue?) {
    if (value is CustomFieldValue.Bool) {
        Text(
            text = if (value.value) "✓" else "—",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    } else {
        Text(
            text = value?.displayValue() ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Ячейка колонки контактов заданного [type]: все значения клиента через запятую.
 * Телефоны кликабельны (`tel:`), email — (`mailto:`); прочие типы показываются обычным текстом.
 */
@Composable
private fun ContactColumnCell(
    type: ContactType,
    client: ClientListItem,
) {
    val values = client.contactsOfType(type)
    if (values.isEmpty()) {
        Text(
            text = "—",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        return
    }

    val uriHandler = LocalUriHandler.current
    val uriPrefix =
        when (type) {
            ContactType.PHONE -> "tel:"
            ContactType.EMAIL -> "mailto:"
            else -> null
        }

    FlowRow {
        values.forEachIndexed { index, value ->
            if (index > 0) {
                Text(text = ", ", style = MaterialTheme.typography.bodyMedium)
            }
            if (uriPrefix != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { uriHandler.openUri(uriPrefix + value) },
                )
            } else {
                Text(text = value, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Нижняя панель групповых действий, появляющаяся при выборе клиентов.
 * [selectedCount] — количество выбранных записей.
 */
@Composable
private fun ClientsBulkActionBar(
    selectedCount: Int,
    onAddToGroup: () -> Unit,
    onDelete: () -> Unit,
    onNotify: () -> Unit,
    onExport: () -> Unit,
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
        IconButton(onClick = onAddToGroup) {
            Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(Res.string.action_add_client_group))
        }
        IconButton(onClick = onDelete) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = stringResource(Res.string.action_delete_selected))
        }
        IconButton(onClick = onNotify) {
            Icon(imageVector = Icons.Default.Notifications, contentDescription = stringResource(Res.string.action_notify_selected))
        }
        IconButton(onClick = onExport) {
            Icon(imageVector = Icons.Default.Share, contentDescription = stringResource(Res.string.action_export_selected))
        }
    }
}
