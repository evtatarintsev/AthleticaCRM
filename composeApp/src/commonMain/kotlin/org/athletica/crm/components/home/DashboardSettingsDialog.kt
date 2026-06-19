package org.athletica.crm.components.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.schemas.settings.BirthdayWindow
import org.athletica.crm.api.schemas.settings.DashboardSettings
import org.athletica.crm.api.schemas.settings.DashboardWidget
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_close
import org.athletica.crm.generated.resources.birthday_window_today
import org.athletica.crm.generated.resources.birthday_window_tomorrow
import org.athletica.crm.generated.resources.birthday_window_week
import org.athletica.crm.generated.resources.cd_limit_decrease
import org.athletica.crm.generated.resources.cd_limit_increase
import org.athletica.crm.generated.resources.cd_reorder_column
import org.athletica.crm.generated.resources.dashboard_label_limit
import org.athletica.crm.generated.resources.dashboard_label_title
import org.athletica.crm.generated.resources.dashboard_settings_title
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableListItemScope

/** Минимально допустимое количество строк в списочном виджете. */
private const val MIN_LIMIT = 1

/** Максимально допустимое количество строк в списочном виджете. */
private const val MAX_LIMIT = 50

/**
 * Диалог настройки виджетов главной страницы.
 * Видимые виджеты — сверху, с возможностью перетаскивания, переименования, настройки
 * и выключения; скрытые — снизу, с возможностью включения. Любое изменение мгновенно
 * вызывает [onSettingsChange]; сохранение на сервер делегируется вышестоящему ViewModel'у.
 *
 * [settings] — текущие настройки дашборда.
 * [onSettingsChange] — обработчик изменения настроек.
 * [onDismiss] — закрытие диалога.
 */
@Composable
fun DashboardSettingsDialog(
    settings: DashboardSettings,
    onSettingsChange: (DashboardSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val visible = settings.orderedVisible()
    val hidden = settings.hidden()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dashboard_settings_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ReorderableColumn(
                    list = visible,
                    onSettle = { fromIndex, toIndex ->
                        val reordered =
                            visible.map { it.id }.toMutableList().apply {
                                add(toIndex, removeAt(fromIndex))
                            }
                        onSettingsChange(settings.copy(layout = reordered))
                    },
                ) { _, widget, _ ->
                    key(widget.id) {
                        ReorderableItem {
                            VisibleWidgetRow(
                                widget = widget,
                                onHide = { onSettingsChange(settings.copy(layout = settings.layout - widget.id)) },
                                onChange = { onSettingsChange(settings.replaceWidget(it)) },
                            )
                        }
                    }
                }

                if (hidden.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    hidden.forEach { widget ->
                        HiddenWidgetRow(
                            widget = widget,
                            onShow = { onSettingsChange(settings.copy(layout = settings.layout + widget.id)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_close)) }
        },
    )
}

/** Строка видимого виджета: drag-handle, поле заголовка, переключатель видимости и настройки. */
@Composable
private fun ReorderableListItemScope.VisibleWidgetRow(
    widget: DashboardWidget,
    onHide: () -> Unit,
    onChange: (DashboardWidget) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = stringResource(Res.string.cd_reorder_column),
                modifier = Modifier.draggableHandle().size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(16.dp))
            OutlinedTextField(
                value = widget.title.orEmpty(),
                onValueChange = { onChange(widget.withTitle(it)) },
                placeholder = { Text(stringResource(widget.defaultTitleRes())) },
                label = { Text(stringResource(Res.string.dashboard_label_title)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Switch(checked = true, onCheckedChange = { onHide() })
        }

        WidgetSettingsControls(widget = widget, onChange = onChange)
    }
}

/** Строка скрытого виджета: имя типа и переключатель для включения. */
@Composable
private fun HiddenWidgetRow(
    widget: DashboardWidget,
    onShow: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Spacer(Modifier.width(40.dp))
        Text(
            text = widget.resolvedTitle(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = false, onCheckedChange = { onShow() })
    }
}

/** Настройки конкретного виджета (под строкой): окно дней рождения, лимит строк и т.п. */
@Composable
private fun WidgetSettingsControls(
    widget: DashboardWidget,
    onChange: (DashboardWidget) -> Unit,
) {
    when (widget) {
        is DashboardWidget.Sessions -> Unit

        is DashboardWidget.Debtors ->
            LimitStepper(limit = widget.limit, onChange = { onChange(widget.copy(limit = it)) })

        is DashboardWidget.Birthdays -> {
            WindowSelector(window = widget.window, onChange = { onChange(widget.copy(window = it)) })
            LimitStepper(limit = widget.limit, onChange = { onChange(widget.copy(limit = it)) })
        }
    }
}

/** Селектор окна дней рождения через набор FilterChip. */
@Composable
private fun WindowSelector(
    window: BirthdayWindow,
    onChange: (BirthdayWindow) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 40.dp, top = 4.dp),
    ) {
        BirthdayWindow.entries.forEach { option ->
            FilterChip(
                selected = window == option,
                onClick = { onChange(option) },
                label = { Text(stringResource(option.labelRes())) },
            )
        }
    }
}

/** Степпер количества строк виджета с ограничением [MIN_LIMIT]..[MAX_LIMIT]. */
@Composable
private fun LimitStepper(
    limit: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 40.dp, top = 4.dp),
    ) {
        Text(
            text = stringResource(Res.string.dashboard_label_limit, limit),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = { onChange((limit - 1).coerceAtLeast(MIN_LIMIT)) },
            enabled = limit > MIN_LIMIT,
        ) {
            Icon(Icons.Rounded.Remove, contentDescription = stringResource(Res.string.cd_limit_decrease))
        }
        IconButton(
            onClick = { onChange((limit + 1).coerceAtMost(MAX_LIMIT)) },
            enabled = limit < MAX_LIMIT,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(Res.string.cd_limit_increase))
        }
    }
}

/** Ключ локализованной подписи окна дней рождения. */
private fun BirthdayWindow.labelRes() =
    when (this) {
        BirthdayWindow.TODAY -> Res.string.birthday_window_today
        BirthdayWindow.TOMORROW -> Res.string.birthday_window_tomorrow
        BirthdayWindow.WEEK -> Res.string.birthday_window_week
    }

/** Возвращает копию виджета с новым заголовком; пустая строка сбрасывает на дефолт (`null`). */
private fun DashboardWidget.withTitle(value: String): DashboardWidget {
    val title = value.ifBlank { null }
    return when (this) {
        is DashboardWidget.Sessions -> copy(title = title)
        is DashboardWidget.Debtors -> copy(title = title)
        is DashboardWidget.Birthdays -> copy(title = title)
    }
}

/** Заменяет виджет в пуле по идентификатору на [updated]. */
private fun DashboardSettings.replaceWidget(updated: DashboardWidget): DashboardSettings = copy(widgets = widgets.map { if (it.id == updated.id) updated else it })
