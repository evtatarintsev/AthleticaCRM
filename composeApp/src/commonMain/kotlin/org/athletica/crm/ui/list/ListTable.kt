package org.athletica.crm.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.athletica.crm.ui.WindowSize

/**
 * Адаптивная таблица со списком элементов.
 *
 * На [WindowSize.COMPACT] рендерит `LazyColumn` из [mobileItem].
 * На [WindowSize.MEDIUM] и [WindowSize.EXPANDED] рендерит табличный вид с заголовком и колонками.
 *
 * [items] — список элементов для отображения.
 * [columns] — декларации колонок таблицы (используются на medium/expanded).
 * [rowKey] — стабильный ключ элемента для LazyColumn.
 * [onRowClick] — обработчик клика по строке.
 * [mobileItem] — слот рендеринга карточки на мобильном (compact) виде.
 *   Принимает элемент, признак выбора и функцию переключения выбора.
 * [windowSize] — текущий размер окна для адаптивной верстки.
 * [selection] — состояние выбора строк. `null` — без выбора.
 * [sort] — текущая сортировка для подсветки иконки в заголовке.
 * [onSortChange] — обработчик клика по заголовку сортируемой колонки.
 * [modifier] — модификатор для внешнего контейнера.
 */
@Composable
fun <T : Any> ListTable(
    items: List<T>,
    columns: List<ListColumn<T>>,
    rowKey: (T) -> Any,
    onRowClick: (T) -> Unit,
    mobileItem: @Composable (item: T, isSelected: Boolean, onToggleSelect: () -> Unit) -> Unit,
    windowSize: WindowSize,
    selection: SelectionState<T>? = null,
    sort: SortState? = null,
    onSortChange: (ColumnId) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (windowSize == WindowSize.COMPACT) {
        LazyColumn(modifier = modifier) {
            items(items, key = rowKey) { item ->
                mobileItem(
                    item,
                    selection?.isSelected(item) ?: false,
                    { selection?.onToggle(item) },
                )
            }
        }
    } else {
        TableLayout(
            items = items,
            columns = columns,
            rowKey = rowKey,
            onRowClick = onRowClick,
            selection = selection,
            sort = sort,
            onSortChange = onSortChange,
            modifier = modifier,
        )
    }
}

/**
 * Табличный вид для medium/expanded.
 * Рендерит строку заголовка, разделитель и LazyColumn со строками данных.
 */
@Composable
private fun <T : Any> TableLayout(
    items: List<T>,
    columns: List<ListColumn<T>>,
    rowKey: (T) -> Any,
    onRowClick: (T) -> Unit,
    selection: SelectionState<T>?,
    sort: SortState?,
    onSortChange: (ColumnId) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(modifier = modifier) {
        TableHeader(columns = columns, selection = selection, sort = sort, onSortChange = onSortChange)
        HorizontalDivider()
        LazyColumn {
            items(items, key = rowKey) { item ->
                TableRow(
                    item = item,
                    columns = columns,
                    selection = selection,
                    onRowClick = onRowClick,
                )
                HorizontalDivider()
            }
        }
    }
}

/**
 * Строка заголовка таблицы.
 */
@Composable
private fun <T : Any> TableHeader(
    columns: List<ListColumn<T>>,
    selection: SelectionState<T>?,
    sort: SortState?,
    onSortChange: (ColumnId) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selection != null) {
                Box(modifier = Modifier.width(48.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    TriStateCheckbox(
                        state = selection.selectAllState,
                        onClick = selection.onToggleAll,
                    )
                }
            }
            columns.forEach { column ->
                val colModifier =
                    when (val w = column.width) {
                        is ColumnWidth.Fixed -> Modifier.width(w.dp)
                        is ColumnWidth.Weight -> Modifier.weight(w.weight)
                    }
                val clickableModifier =
                    if (column.sortable) {
                        colModifier.clickable { onSortChange(column.id) }
                    } else {
                        colModifier
                    }
                Row(
                    modifier = clickableModifier.fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        column.header()
                    }
                    if (column.sortable && sort?.columnId == column.id) {
                        val icon =
                            if (sort.direction == SortDirection.Asc) {
                                Icons.Default.KeyboardArrowUp
                            } else {
                                Icons.Default.KeyboardArrowDown
                            }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Строка данных таблицы.
 */
@Composable
private fun <T : Any> TableRow(
    item: T,
    columns: List<ListColumn<T>>,
    selection: SelectionState<T>?,
    onRowClick: (T) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable { onRowClick(item) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selection != null) {
            Box(
                modifier =
                    Modifier
                        .width(48.dp)
                        .fillMaxHeight()
                        .clickable { selection.onToggle(item) },
                contentAlignment = Alignment.Center,
            ) {
                Checkbox(
                    checked = selection.isSelected(item),
                    onCheckedChange = { selection.onToggle(item) },
                )
            }
        }
        columns.forEach { column ->
            val colModifier =
                when (val w = column.width) {
                    is ColumnWidth.Fixed -> Modifier.width(w.dp).fillMaxHeight()
                    is ColumnWidth.Weight -> Modifier.weight(w.weight).fillMaxHeight()
                }
            Box(
                modifier = colModifier.wrapContentSize(align = Alignment.CenterStart),
            ) {
                column.cell(item)
            }
        }
    }
}
