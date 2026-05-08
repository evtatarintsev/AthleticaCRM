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
import org.athletica.crm.core.customfields.CustomFieldDefinition
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_close
import org.athletica.crm.generated.resources.action_display_settings
import org.athletica.crm.generated.resources.label_birth_year
import org.athletica.crm.generated.resources.label_debt
import org.athletica.crm.generated.resources.label_gender
import org.athletica.crm.generated.resources.label_person_name
import org.athletica.crm.generated.resources.section_columns
import org.athletica.crm.generated.resources.section_custom_columns
import org.jetbrains.compose.resources.stringResource

/**
 * Диалог настроек отображения таблицы клиентов.
 * Позволяет включать/выключать видимость стандартных и кастомных колонок.
 * Изменения применяются сразу через [onSettingsChange].
 *
 * [settings] — текущие настройки отображения.
 * [availableCustomFields] — список доступных кастомных полей для добавления в таблицу.
 * [onSettingsChange] — вызывается при любом изменении.
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.action_display_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Видимые колонки
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(Res.string.section_columns),
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
                            text = stringResource(Res.string.label_person_name),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        )
                    }

                    // Стандартные опциональные колонки
                    ClientColumn.Standard.entries.forEach { column ->
                        val checked = column in settings.columns
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val newColumns =
                                            if (checked) {
                                                settings.columns.filterNot { it.apiKey == column.apiKey }
                                            } else {
                                                settings.columns + column
                                            }
                                        onSettingsChange(settings.copy(columns = newColumns))
                                    },
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = null,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text =
                                    when (column) {
                                        ClientColumn.Standard.Gender -> stringResource(Res.string.label_gender)
                                        ClientColumn.Standard.BirthYear -> stringResource(Res.string.label_birth_year)
                                        ClientColumn.Standard.Debt -> stringResource(Res.string.label_debt)
                                    },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    // Кастомные поля (если есть)
                    if (availableCustomFields.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(Res.string.section_custom_columns),
                            style = MaterialTheme.typography.labelLarge,
                        )

                        availableCustomFields.forEach { field ->
                            val checked = field.fieldKey in settings.columns.map { it.apiKey }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val newColumns =
                                                if (checked) {
                                                    settings.columns.filterNot { it.apiKey == field.fieldKey }
                                                } else {
                                                    settings.columns + ClientColumn.Custom(field.fieldKey, field.label)
                                                }
                                            onSettingsChange(settings.copy(columns = newColumns))
                                        },
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = null,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = field.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_close)) }
        },
    )
}
