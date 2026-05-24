package org.athletica.crm.ui.list

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.list_action_close_search
import org.athletica.crm.generated.resources.list_action_column_settings
import org.athletica.crm.generated.resources.list_action_filters
import org.athletica.crm.generated.resources.list_action_filters_with_count
import org.athletica.crm.ui.WindowSize
import org.jetbrains.compose.resources.stringResource

/**
 * Состояние и колбэки панели фильтра.
 * `null` для параметра [filterPanel] в [ListPageScaffold] означает «без фильтров».
 *
 * [visible] — отображается ли панель в данный момент.
 * [onDismiss] — скрыть панель без применения.
 * [onReset] — сбросить фильтры.
 * [onSaveAsView] — сохранить текущий фильтр как вид. `null` — кнопка скрыта.
 * [onApply] — применить фильтр.
 * [applyEnabled] — доступность кнопки применения.
 * [applyLabel] — текст кнопки «Применить».
 * [content] — слот с конкретными полями фильтра раздела.
 */
data class ListPageFilterPanel(
    val visible: Boolean,
    val onDismiss: () -> Unit,
    val onReset: () -> Unit,
    val onSaveAsView: (() -> Unit)?,
    val onApply: () -> Unit,
    val applyEnabled: Boolean,
    val applyLabel: String,
    val content: @Composable ColumnScope.() -> Unit,
)

/**
 * Корневой скаффолд для страницы со списком.
 *
 * Адаптирует верстку под три размера окна ([WindowSize]):
 * - COMPACT: мобильный — M3 [SearchBar] виден всегда; по тапу разворачивается в
 *   полноэкранный режим, содержимое страницы (saved views, фильтры, список) рендерится
 *   внутри expanded-слота — список остаётся виден во время поиска. Фильтры — bottom sheet.
 * - MEDIUM: планшет/узкий десктоп — [SearchBar] inline сверху, expansion заблокирована;
 *   фильтры — bottom sheet.
 * - EXPANDED: широкий десктоп — [SearchBar] inline сверху, фильтры — боковая панель.
 *
 * [title] — заголовок страницы для [LocalListPageTopBar].
 * [subtitle] — подзаголовок (количество записей, активный вид и т.п.). `null` — скрыт.
 * [windowSize] — текущий размер окна.
 * [searchQuery] — текущий поисковый запрос.
 * [onSearchQueryChange] — обработчик изменения запроса.
 * [searchPlaceholder] — подсказка в поле поиска.
 * [savedViews] — список сохранённых видов для [SavedViewRow].
 * [activeSavedViewId] — идентификатор активного вида.
 * [onSaveCurrentView] — сохранить текущий вид. `null` — кнопка скрыта.
 * [activeFilterCount] — количество активных фильтров (0 — без счётчика на кнопке).
 * [onOpenFilters] — открыть панель фильтров.
 * [quickFilterChips] — слот для чипов активных фильтров (с кнопкой удаления).
 * [filterPanel] — состояние и контент панели фильтров. `null` — без фильтров.
 * [onOpenColumnSettings] — открыть настройки колонок. `null` — кнопка скрыта.
 * [sortChipLabel] — текст чипа сортировки на COMPACT. `null` — чип скрыт.
 * [onOpenSortDialog] — открыть диалог сортировки. `null` — чип скрыт.
 * [fab] — FAB кнопка создания.
 * [bulkActionBar] — нижняя панель массовых действий. `null` — FAB виден, панель скрыта.
 * [content] — основное содержимое (таблица или список карточек).
 * [modifier] — модификатор.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListPageScaffold(
    title: String,
    subtitle: String?,
    windowSize: WindowSize,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchPlaceholder: String,
    savedViews: List<SavedView>,
    activeSavedViewId: SavedViewId?,
    onSaveCurrentView: (() -> Unit)?,
    activeFilterCount: Int,
    onOpenFilters: () -> Unit,
    quickFilterChips: @Composable RowScope.() -> Unit,
    filterPanel: ListPageFilterPanel?,
    onOpenColumnSettings: (() -> Unit)?,
    sortChipLabel: String?,
    onOpenSortDialog: (() -> Unit)?,
    fab: @Composable () -> Unit,
    bulkActionBar: (@Composable () -> Unit)?,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val topBarController = LocalListPageTopBar.current
    LaunchedEffect(title, subtitle) { topBarController.set(title, subtitle) }

    var expanded by remember { mutableStateOf(false) }
    val expandedOnCompact = expanded && windowSize == WindowSize.COMPACT

    val showSavedViewRow = savedViews.isNotEmpty() || onSaveCurrentView != null

    val pageBody: @Composable () -> Unit = {
        if (showSavedViewRow) {
            SavedViewRow(
                savedViews = savedViews,
                activeSavedViewId = activeSavedViewId,
                onSaveCurrentView = onSaveCurrentView,
            )
        }
        FilterControlRow(
            activeFilterCount = activeFilterCount,
            onOpenFilters = onOpenFilters,
            quickFilterChips = quickFilterChips,
            onOpenColumnSettings = onOpenColumnSettings,
            sortChipLabel = sortChipLabel,
            onOpenSortDialog = onOpenSortDialog,
            windowSize = windowSize,
        )
        content()
    }

    val searchBarBlock: @Composable () -> Unit = {
        SearchBarBlock(
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            placeholder = searchPlaceholder,
            expanded = expandedOnCompact,
            onExpandedChange = { newExpanded ->
                if (windowSize == WindowSize.COMPACT) {
                    expanded = newExpanded
                }
            },
            expandedContent = pageBody,
        )
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (bulkActionBar == null && !expandedOnCompact) {
                fab()
            }
        },
        bottomBar = { bulkActionBar?.invoke() },
    ) { innerPadding ->
        if (windowSize == WindowSize.EXPANDED && filterPanel?.visible == true) {
            Row(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                Column(modifier = Modifier.weight(1f)) {
                    searchBarBlock()
                    pageBody()
                }
                FilterSidePanel(filterPanel = filterPanel)
            }
        } else {
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                searchBarBlock()
                if (!expandedOnCompact) {
                    pageBody()
                }
            }
        }

        if (windowSize != WindowSize.EXPANDED && filterPanel?.visible == true) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = filterPanel.onDismiss,
                sheetState = sheetState,
            ) {
                FilterPanelContent(
                    onReset = filterPanel.onReset,
                    onSaveAsView = filterPanel.onSaveAsView,
                    onApply = filterPanel.onApply,
                    applyEnabled = filterPanel.applyEnabled,
                    applyLabel = filterPanel.applyLabel,
                    content = filterPanel.content,
                )
            }
        }
    }
}

/**
 * Универсальный блок M3 [SearchBar] для всех размеров окна.
 *
 * На MEDIUM/EXPANDED — постоянно видимое поле (expansion заблокирована вызывающей
 * стороной через [expanded]=`false`). На COMPACT при [expanded]=`true` бар становится
 * полноэкранным, а внутри content-слота рендерится [expandedContent] — список и
 * фильтры остаются видны во время поиска.
 *
 * [searchQuery] — текущий запрос.
 * [onSearchQueryChange] — обработчик ввода.
 * [placeholder] — подсказка.
 * [expanded] — раскрыт ли бар (для текущей вёрстки имеет смысл только на COMPACT).
 * [onExpandedChange] — переключатель expanded; вызывающая сторона игнорирует на больших экранах.
 * [expandedContent] — контент, показываемый внутри раскрытого бара (видим только на COMPACT в expanded).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBarBlock(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    placeholder: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    expandedContent: @Composable () -> Unit,
) {
    SearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onSearch = { /* ввод уже триггерит debounce-загрузку в ListPageViewModel */ },
                expanded = expanded,
                onExpandedChange = onExpandedChange,
                placeholder = { Text(placeholder) },
                leadingIcon = {
                    if (expanded) {
                        IconButton(onClick = { onExpandedChange(false) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.list_action_close_search),
                            )
                        }
                    } else {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null)
                    }
                },
            )
        },
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        expandedContent()
    }
}

/**
 * Панель управления фильтрами: кнопка фильтров, чипы активных фильтров, сортировка, настройки.
 *
 * [activeFilterCount] — количество активных фильтров (0 — без счётчика на кнопке).
 * [onOpenFilters] — открыть панель фильтров.
 * [quickFilterChips] — слот для чипов активных фильтров.
 * [onOpenColumnSettings] — открыть настройки колонок. `null` — кнопка скрыта.
 * [sortChipLabel] — текст чипа сортировки на COMPACT. `null` — чип скрыт.
 * [onOpenSortDialog] — открыть диалог сортировки. `null` — чип скрыт.
 * [windowSize] — текущий размер окна (определяет видимость чипа сортировки).
 */
@Composable
private fun FilterControlRow(
    activeFilterCount: Int,
    onOpenFilters: () -> Unit,
    quickFilterChips: @Composable RowScope.() -> Unit,
    onOpenColumnSettings: (() -> Unit)?,
    sortChipLabel: String?,
    onOpenSortDialog: (() -> Unit)?,
    windowSize: WindowSize,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val filtersLabel =
            if (activeFilterCount > 0) {
                stringResource(Res.string.list_action_filters_with_count, activeFilterCount)
            } else {
                stringResource(Res.string.list_action_filters)
            }
        OutlinedButton(onClick = onOpenFilters) {
            Icon(imageVector = Icons.Default.FilterList, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text(filtersLabel)
        }
        Spacer(modifier = Modifier.width(8.dp))
        quickFilterChips()
        if (windowSize == WindowSize.COMPACT && sortChipLabel != null && onOpenSortDialog != null) {
            Spacer(modifier = Modifier.width(8.dp))
            AssistChip(
                onClick = onOpenSortDialog,
                label = { Text(sortChipLabel) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) },
            )
        }
        if (onOpenColumnSettings != null) {
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenColumnSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(Res.string.list_action_column_settings),
                )
            }
        }
    }
}

/**
 * Боковая панель фильтров для expanded (desktop).
 * Рендерится рядом с основным контентом в [Row].
 */
@Composable
private fun FilterSidePanel(filterPanel: ListPageFilterPanel) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.width(320.dp).fillMaxHeight(),
    ) {
        FilterPanelContent(
            onReset = filterPanel.onReset,
            onSaveAsView = filterPanel.onSaveAsView,
            onApply = filterPanel.onApply,
            applyEnabled = filterPanel.applyEnabled,
            applyLabel = filterPanel.applyLabel,
            content = filterPanel.content,
        )
    }
}
