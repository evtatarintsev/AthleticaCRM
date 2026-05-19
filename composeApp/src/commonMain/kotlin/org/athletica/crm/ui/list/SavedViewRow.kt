package org.athletica.crm.ui.list

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.list_action_delete_view
import org.athletica.crm.generated.resources.list_action_rename_view
import org.athletica.crm.generated.resources.list_action_save_view_short
import org.jetbrains.compose.resources.stringResource

/**
 * Горизонтальная строка сохранённых видов списка.
 * Системные виды отображаются как [FilterChip] без контекстного меню.
 * Пользовательские виды поддерживают долгое нажатие для переименования и удаления.
 * В конце строки — кнопка «+ Сохранить» если [onSaveCurrentView] задан.
 *
 * [savedViews] — список всех видов (системных и пользовательских).
 * [activeSavedViewId] — идентификатор активного вида. `null` — ни один не выбран.
 * [onSaveCurrentView] — сохранить текущий вид. `null` — кнопка скрыта.
 * [modifier] — модификатор.
 */
@Composable
internal fun SavedViewRow(
    savedViews: List<SavedView>,
    activeSavedViewId: SavedViewId?,
    onSaveCurrentView: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        savedViews.forEach { view ->
            SavedViewChip(
                view = view,
                isSelected = view.id == activeSavedViewId,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        if (onSaveCurrentView != null) {
            AssistChip(
                onClick = onSaveCurrentView,
                label = { Text(stringResource(Res.string.list_action_save_view_short)) },
                leadingIcon = { Icon(imageVector = Icons.Default.Add, contentDescription = null) },
            )
        }
    }
}

/**
 * Отдельный чип сохранённого вида.
 * Пользовательские виды открывают контекстное меню при долгом нажатии.
 */
@Composable
private fun SavedViewChip(
    view: SavedView,
    isSelected: Boolean,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    if (view.id.isSystem) {
        FilterChip(
            selected = isSelected,
            onClick = view.onApply,
            label = { Text(view.name) },
        )
    } else {
        androidx.compose.foundation.layout.Box {
            FilterChip(
                selected = isSelected,
                onClick = view.onApply,
                label = { Text(view.name) },
                modifier =
                    Modifier.combinedClickable(
                        onClick = view.onApply,
                        onLongClick = { menuExpanded = true },
                    ),
            )
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                if (view.onRename != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.list_action_rename_view)) },
                        onClick = {
                            menuExpanded = false
                            view.onRename.invoke()
                        },
                    )
                }
                if (view.onDelete != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.list_action_delete_view)) },
                        onClick = {
                            menuExpanded = false
                            view.onDelete.invoke()
                        },
                    )
                }
            }
        }
    }
}
