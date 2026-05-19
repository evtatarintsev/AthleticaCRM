package org.athletica.crm.ui.list

import androidx.compose.ui.state.ToggleableState

/**
 * Состояние выбора строк списка.
 * Управление выбором — на стороне страницы; [ListTable] только отображает и проксирует события.
 *
 * [isSelected] — проверка, выбран ли конкретный элемент.
 * [onToggle] — переключение выбора одного элемента.
 * [onToggleAll] — клик по чекбоксу в шапке (вкл/выкл всё).
 * [selectAllState] — состояние чекбокса в шапке (`Off` / `On` / `Indeterminate`).
 */
class SelectionState<T : Any>(
    val isSelected: (T) -> Boolean,
    val onToggle: (T) -> Unit,
    val onToggleAll: () -> Unit,
    val selectAllState: ToggleableState,
)
