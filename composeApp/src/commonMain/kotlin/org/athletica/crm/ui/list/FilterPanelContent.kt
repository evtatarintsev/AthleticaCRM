package org.athletica.crm.ui.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.list_action_reset_filters
import org.athletica.crm.generated.resources.list_action_save_view
import org.athletica.crm.generated.resources.list_label_filters_title
import org.jetbrains.compose.resources.stringResource

/**
 * Внутренний компонент — общая верстка содержимого панели фильтров.
 * Используется как в [ModalBottomSheet] (compact/medium), так и в боковой панели (expanded).
 *
 * [onReset] — сброс всех фильтров.
 * [onSaveAsView] — сохранение текущего фильтра как вида. `null` — кнопка скрыта.
 * [onApply] — применение фильтра.
 * [applyEnabled] — доступность кнопки «Применить».
 * [applyLabel] — текст кнопки «Применить» (может содержать количество результатов).
 * [content] — слот с конкретными полями фильтра раздела.
 * [modifier] — модификатор для внешнего контейнера.
 */
@Composable
internal fun FilterPanelContent(
    onReset: () -> Unit,
    onSaveAsView: (() -> Unit)?,
    onApply: () -> Unit,
    applyEnabled: Boolean,
    applyLabel: String,
    content: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.list_label_filters_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onReset) {
                Text(stringResource(Res.string.list_action_reset_filters))
            }
        }
        HorizontalDivider()
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            content()
        }
        HorizontalDivider()
        if (onSaveAsView != null) {
            TextButton(
                onClick = onSaveAsView,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Icon(imageVector = Icons.Default.Star, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(Res.string.list_action_save_view))
            }
        }
        Button(
            onClick = onApply,
            enabled = applyEnabled,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(applyLabel)
        }
    }
}
