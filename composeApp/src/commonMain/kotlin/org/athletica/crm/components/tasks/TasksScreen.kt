package org.athletica.crm.components.tasks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.tasks.TaskListItemSchema
import org.athletica.crm.components.settings.DisplaySettingsViewModel
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_create_task
import org.athletica.crm.generated.resources.empty_search_results
import org.athletica.crm.generated.resources.list_action_apply
import org.athletica.crm.generated.resources.tasks_col_assignee
import org.athletica.crm.generated.resources.tasks_col_due_date
import org.athletica.crm.generated.resources.tasks_col_status
import org.athletica.crm.generated.resources.tasks_col_title
import org.athletica.crm.generated.resources.tasks_empty
import org.athletica.crm.generated.resources.tasks_filter_mine
import org.athletica.crm.generated.resources.tasks_filter_only_mine
import org.athletica.crm.generated.resources.tasks_load_error
import org.athletica.crm.generated.resources.tasks_search_placeholder
import org.athletica.crm.generated.resources.tasks_subtitle_count
import org.athletica.crm.generated.resources.tasks_title
import org.athletica.crm.generated.resources.tasks_view_all
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
import org.athletica.crm.ui.list.SortBottomSheet
import org.athletica.crm.ui.list.SortDirection
import org.athletica.crm.ui.list.SortableColumn
import org.jetbrains.compose.resources.stringResource

/**
 * Экран списка задач.
 * Использует [ListPageScaffold] для унифицированного UI с поиском, фильтрами и сохранёнными видами.
 *
 * [api] — клиент API.
 * [displaySettingsVm] — ViewModel для сохранения настроек отображения.
 * [windowSize] — текущий размер окна для адаптивной верстки.
 * [onNavigateToCreate] — переход к созданию задачи.
 * [onTaskClick] — переход к деталям задачи.
 * [refreshKey] — инкремент принудительно перезагружает список.
 */
@Composable
fun TasksScreen(
    api: ApiClient,
    displaySettingsVm: DisplaySettingsViewModel,
    windowSize: WindowSize,
    onNavigateToCreate: () -> Unit = {},
    onTaskClick: (TaskId) -> Unit = {},
    refreshKey: Int = 0,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel =
        remember {
            ListPageViewModel(TasksPageDelegate(api, displaySettingsVm), scope)
        }

    LaunchedEffect(refreshKey) { viewModel.load() }

    var filterSheetVisible by remember { mutableStateOf(false) }
    var sortSheetVisible by remember { mutableStateOf(false) }
    var filterDraft by remember { mutableStateOf(viewModel.state.filter) }

    LaunchedEffect(filterSheetVisible) {
        if (filterSheetVisible) {
            filterDraft = viewModel.state.filter
        }
    }

    val title = stringResource(Res.string.tasks_title)
    val allLabel = stringResource(Res.string.tasks_view_all)
    val myLabel = stringResource(Res.string.tasks_filter_mine)
    val total = (viewModel.state.data as? ListData.Loaded)?.total ?: 0
    val subtitleTemplate = stringResource(Res.string.tasks_subtitle_count, total)
    val subtitle = computeSubtitle(subtitleTemplate, viewModel.state.activeSavedViewId, myLabel)

    val savedViews =
        remember(allLabel, myLabel) {
            listOf(
                SavedView(
                    id = TasksSavedViews.ALL.id,
                    name = allLabel,
                    onApply = { viewModel.applySystemView(TasksSavedViews.ALL) },
                ),
                SavedView(
                    id = TasksSavedViews.MY_TASKS.id,
                    name = myLabel,
                    onApply = { viewModel.applySystemView(TasksSavedViews.MY_TASKS) },
                ),
            )
        }

    val titleCol = stringResource(Res.string.tasks_col_title)
    val statusCol = stringResource(Res.string.tasks_col_status)
    val assigneeCol = stringResource(Res.string.tasks_col_assignee)
    val dueDateCol = stringResource(Res.string.tasks_col_due_date)

    val columns =
        listOf(
            ListColumn(
                id = TasksColumns.Title,
                header = { Text(titleCol) },
                width = ColumnWidth.Weight(1f),
                sortable = true,
                cell = { item: TaskListItemSchema ->
                    Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            ),
            ListColumn(
                id = TasksColumns.Status,
                header = { Text(statusCol) },
                width = ColumnWidth.Fixed(120.dp),
                sortable = true,
                cell = { item: TaskListItemSchema ->
                    TaskStatusBadge(status = item.status)
                },
            ),
            ListColumn(
                id = TasksColumns.Assignee,
                header = { Text(assigneeCol) },
                width = ColumnWidth.Fixed(160.dp),
                sortable = true,
                cell = { item: TaskListItemSchema ->
                    Text(
                        item.assigneeName ?: "—",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            ),
            ListColumn(
                id = TasksColumns.DueDate,
                header = { Text(dueDateCol) },
                width = ColumnWidth.Fixed(140.dp),
                sortable = true,
                cell = { item: TaskListItemSchema ->
                    Text(item.dueDate?.let(::formatDueDate) ?: "—", maxLines = 1)
                },
            ),
        )

    val sortChipLabel =
        viewModel.state.sort?.let { s ->
            val dir = if (s.direction == SortDirection.Asc) "↑" else "↓"
            val colName =
                when (s.columnId) {
                    TasksColumns.Title -> titleCol
                    TasksColumns.Status -> statusCol
                    TasksColumns.Assignee -> assigneeCol
                    TasksColumns.DueDate -> dueDateCol
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
        searchPlaceholder = stringResource(Res.string.tasks_search_placeholder),
        savedViews = savedViews,
        activeSavedViewId = viewModel.state.activeSavedViewId,
        onSaveCurrentView = null,
        activeFilterCount = viewModel.state.filter.activeCount,
        onOpenFilters = { filterSheetVisible = true },
        quickFilterChips = {
            if (viewModel.state.filter.onlyMine) {
                FilterChip(
                    selected = true,
                    onClick = {
                        viewModel.setFilter(viewModel.state.filter.copy(onlyMine = false))
                    },
                    label = { Text(stringResource(Res.string.tasks_filter_only_mine)) },
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
            viewModel.state.filter.statuses.forEach { status ->
                FilterChip(
                    selected = true,
                    onClick = {
                        viewModel.setFilter(
                            viewModel.state.filter.copy(
                                statuses = viewModel.state.filter.statuses - status,
                            ),
                        )
                    },
                    label = { Text(stringResource(statusLabelRes(status))) },
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
                onReset = { filterDraft = TasksFilter() },
                onSaveAsView = null,
                onApply = {
                    viewModel.setFilter(filterDraft)
                    filterSheetVisible = false
                },
                applyEnabled = true,
                applyLabel = applyLabel,
                content = {
                    TasksFilterContent(
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
                text = { Text(stringResource(Res.string.action_create_task)) },
            )
        },
        bulkActionBar = null,
        content = {
            TasksContent(
                viewModel = viewModel,
                columns = columns,
                windowSize = windowSize,
                onTaskClick = onTaskClick,
            )
        },
        modifier = modifier,
    )

    if (sortSheetVisible) {
        SortBottomSheet(
            columns =
                listOf(
                    SortableColumn(TasksColumns.Title, titleCol),
                    SortableColumn(TasksColumns.Status, statusCol),
                    SortableColumn(TasksColumns.Assignee, assigneeCol),
                    SortableColumn(TasksColumns.DueDate, dueDateCol),
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

/** Вычисляет подзаголовок топбара: количество задач и опционально имя активного вида. */
@Composable
private fun computeSubtitle(
    countText: String,
    activeSavedViewId: SavedViewId?,
    myTasksLabel: String,
): String? {
    val viewName =
        when (activeSavedViewId) {
            TasksSavedViews.MY_TASKS.id -> myTasksLabel
            TasksSavedViews.ALL.id -> null
            else -> null
        }
    return if (viewName != null) "$countText · $viewName" else countText
}

/** Основное содержимое списка задач с адаптацией под состояния загрузки и пустого списка. */
@Composable
private fun TasksContent(
    viewModel: ListPageViewModel<TaskListItemSchema, TasksFilter>,
    columns: List<ListColumn<TaskListItemSchema>>,
    windowSize: WindowSize,
    onTaskClick: (TaskId) -> Unit,
) {
    when (viewModel.state.data) {
        is ListData.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is ListData.Error -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(Res.string.tasks_load_error),
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
                        Res.string.tasks_empty
                    }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(emptyRes),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                if (windowSize == WindowSize.COMPACT) {
                    LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                        items(viewModel.visible, key = { it.id.value }) { task ->
                            TaskMobileItem(
                                task = task,
                                isSelected = false,
                                onToggleSelect = {},
                                onClick = { onTaskClick(task.id) },
                            )
                        }
                    }
                } else {
                    ListTable(
                        items = viewModel.visible,
                        columns = columns,
                        rowKey = { it.id.value },
                        onRowClick = { onTaskClick(it.id) },
                        mobileItem = { task, isSelected, onToggleSelect ->
                            TaskMobileItem(
                                task = task,
                                isSelected = isSelected,
                                onToggleSelect = onToggleSelect,
                                onClick = { onTaskClick(task.id) },
                            )
                        },
                        windowSize = windowSize,
                        selection = null,
                        sort = viewModel.state.sort,
                        onSortChange = { columnId -> viewModel.cycleSort(columnId) },
                    )
                }
            }
        }
    }
}
