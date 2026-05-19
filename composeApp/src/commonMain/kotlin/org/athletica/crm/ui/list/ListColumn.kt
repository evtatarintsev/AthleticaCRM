package org.athletica.crm.ui.list

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp

/** Ширина колонки в таблице. */
sealed class ColumnWidth {
    /** Фиксированная ширина (например, для поля пола или даты). */
    data class Fixed(val dp: Dp) : ColumnWidth()

    /** Доля от оставшегося места (для имени и подобных колонок переменной длины). */
    data class Weight(val weight: Float) : ColumnWidth()
}

/**
 * Декларация колонки таблицы.
 *
 * [id] — стабильный идентификатор, используется в сортировке и сохранении настроек.
 * [header] — слот рендеринга заголовка (обычно `Text(stringResource(...))`).
 * [width] — ширина колонки.
 * [alignment] — горизонтальное выравнивание содержимого ячейки.
 * [sortable] — если `true`, заголовок становится кликабельным и показывает индикатор сортировки.
 * [cell] — слот рендеринга ячейки для данного элемента.
 */
data class ListColumn<T>(
    val id: ColumnId,
    val header: @Composable () -> Unit,
    val width: ColumnWidth,
    val alignment: Alignment.Horizontal = Alignment.Start,
    val sortable: Boolean = false,
    val cell: @Composable (T) -> Unit,
)
