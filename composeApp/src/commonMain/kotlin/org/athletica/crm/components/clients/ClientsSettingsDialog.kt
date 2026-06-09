package org.athletica.crm.components.clients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.schemas.clients.ClientField
import org.athletica.crm.core.contacts.ContactType
import org.athletica.crm.core.customfields.CustomFieldDefinition
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_close
import org.athletica.crm.generated.resources.action_display_settings
import org.athletica.crm.generated.resources.cd_reorder_column
import org.athletica.crm.generated.resources.label_person_name
import org.athletica.crm.generated.resources.section_columns
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableListItemScope

/**
 * Диалог настроек отображения таблицы клиентов.
 * Показывает единый список колонок (стандартных и кастомных): включённые сверху
 * с возможностью перетаскивания, выключенные — снизу. Колонка «Имя» всегда первая,
 * её нельзя ни выключить, ни переместить. Любое изменение мгновенно вызывает
 * [onSettingsChange]; сохранение на сервер делегируется вышестоящему ViewModel'у.
 *
 * [settings] — текущие настройки отображения.
 * [availableCustomFields] — список доступных кастомных полей для добавления в таблицу.
 * [onDismiss] — закрытие диалога.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientsSettingsDialog(
    settings: ClientDisplaySettings,
    availableCustomFields: List<CustomFieldDefinition> = emptyList(),
    onSettingsChange: (ClientDisplaySettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val disabled = settings.disabledColumns(availableCustomFields)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.action_display_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(Res.string.section_columns),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 4.dp),
                )

                NameRow()

                ReorderableColumn(
                    list = settings.columns,
                    onSettle = { fromIndex, toIndex ->
                        val reordered =
                            settings.columns.toMutableList().apply {
                                add(toIndex, removeAt(fromIndex))
                            }
                        onSettingsChange(settings.copy(columns = reordered))
                    },
                ) { _, column, _ ->
                    key(column.apiKey) {
                        ReorderableItem {
                            EnabledColumnRow(
                                column = column,
                                onDisable = {
                                    onSettingsChange(
                                        settings.copy(columns = settings.columns.filterNot { it.apiKey == column.apiKey }),
                                    )
                                },
                            )
                        }
                    }
                }

                if (disabled.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    disabled.forEach { column ->
                        DisabledColumnRow(
                            column = column,
                            onEnable = {
                                onSettingsChange(settings.copy(columns = settings.columns + column))
                            },
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

/** Возвращает список колонок, которые не выбраны в [settings], в стабильном порядке. */
private fun ClientDisplaySettings.disabledColumns(availableCustomFields: List<CustomFieldDefinition>): List<ClientColumn> {
    val selectedKeys = columns.map { it.apiKey }.toSet()
    val standard = ClientField.entries.filter { it.apiKey !in selectedKeys }.map { ClientColumn.Standard(it) }
    val contact =
        ContactType.entries
            .map { ClientColumn.Contact(it) }
            .filter { it.apiKey !in selectedKeys }
    val custom =
        availableCustomFields
            .filter { it.fieldKey.value !in selectedKeys }
            .map { ClientColumn.Custom(it.fieldKey.value, it.label) }
    return standard + contact + custom
}

/** Строка «Имя» — всегда включена, не редактируется, не перетаскивается. */
@Composable
private fun NameRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Spacer(Modifier.width(40.dp))
        Text(
            text = stringResource(Res.string.label_person_name),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = true,
            onCheckedChange = null,
            enabled = false,
        )
    }
}

/** Строка включённой колонки с drag-handle и Switch'ом для выключения. */
@Composable
private fun ReorderableListItemScope.EnabledColumnRow(
    column: ClientColumn,
    onDisable: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.DragHandle,
            contentDescription = stringResource(Res.string.cd_reorder_column),
            modifier = Modifier.draggableHandle().size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = column.label(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = true,
            onCheckedChange = { onDisable() },
        )
    }
}

/** Строка выключенной колонки: drag-handle не показывается. */
@Composable
private fun DisabledColumnRow(
    column: ClientColumn,
    onEnable: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Spacer(Modifier.width(40.dp))
        Text(
            text = column.label(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = false,
            onCheckedChange = { onEnable() },
        )
    }
}

/** Возвращает локализованное название колонки. */
@Composable
private fun ClientColumn.label(): String =
    when (this) {
        is ClientColumn.Standard -> stringResource(clientField.labelRes())
        is ClientColumn.Custom -> label
        is ClientColumn.Contact -> stringResource(type.labelRes())
    }
