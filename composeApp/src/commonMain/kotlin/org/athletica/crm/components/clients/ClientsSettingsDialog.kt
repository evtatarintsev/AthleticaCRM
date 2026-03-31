package org.athletica.crm.components.clients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Диалог настроек отображения таблицы клиентов.
 * Изменения применяются сразу через [onSettingsChange].
 *
 * [settings] — текущие настройки отображения.
 * [onSettingsChange] — вызывается при любом изменении.
 * [onDismiss] — закрытие диалога.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientsSettingsDialog(
    settings: ClientDisplaySettings,
    onSettingsChange: (ClientDisplaySettings) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки отображения") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Видимые колонки
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Колонки",
                        style = MaterialTheme.typography.labelLarge,
                    )

                    // Имя — всегда видна, нельзя снять
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = true,
                            onCheckedChange = null,
                            colors =
                                CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                    checkmarkColor = MaterialTheme.colorScheme.surface,
                                ),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Имя",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        )
                    }

                    // Опциональные колонки
                    ClientColumn.entries.forEach { column ->
                        val checked = column in settings.visibleColumns
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val newColumns =
                                            if (checked) {
                                                settings.visibleColumns - column
                                            } else {
                                                settings.visibleColumns + column
                                            }
                                        onSettingsChange(settings.copy(visibleColumns = newColumns))
                                    },
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = null,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = column.label,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
    )
}
