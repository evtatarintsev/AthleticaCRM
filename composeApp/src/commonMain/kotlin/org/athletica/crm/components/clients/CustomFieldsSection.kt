package org.athletica.crm.components.clients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import org.athletica.crm.api.schemas.customfields.CustomFieldDefinitionSchema
import org.athletica.crm.api.schemas.customfields.CustomFieldTypeConfig
import org.athletica.crm.api.schemas.customfields.CustomFieldValue
import org.athletica.crm.api.schemas.customfields.CustomFieldValues
import org.athletica.crm.api.schemas.customfields.toTypeConfig
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_cancel
import org.athletica.crm.generated.resources.action_clear
import org.athletica.crm.generated.resources.action_ok
import org.athletica.crm.generated.resources.hint_date_format
import org.athletica.crm.generated.resources.hint_select_option
import org.athletica.crm.generated.resources.section_additional_attributes
import org.jetbrains.compose.resources.stringResource

/**
 * Секция кастомных полей формы клиента.
 * Рендерит инпуты для каждого определения из [customFields.definitions].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomFieldsSection(
    customFields: CustomFieldValues,
    onUpdate: (CustomFieldValues) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val defs = customFields.definitions
    if (defs.isEmpty()) return

    var datePickerField by remember { mutableStateOf<String?>(null) }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier,
    ) {
        HorizontalDivider()
        Text(
            text = stringResource(Res.string.section_additional_attributes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        defs.forEach { def ->
            CustomFieldInput(
                def = def,
                customFields = customFields,
                onUpdate = onUpdate,
                enabled = enabled,
                onRequestDatePicker = { datePickerField = def.fieldKey },
            )
        }
    }

    if (datePickerField != null) {
        val fieldKey = datePickerField!!
        val currentDate = (customFields[fieldKey] as? CustomFieldValue.Date)?.value
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = currentDate?.toEpochDays()?.let { it * 86_400_000L },
            )
        DatePickerDialog(
            onDismissRequest = { datePickerField = null },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = LocalDate.fromEpochDays((millis / 86_400_000L).toInt())
                        val updated =
                            customFields.with(CustomFieldValue.Date(fieldKey, date))
                                .fold({ customFields }, { it })
                        onUpdate(updated)
                    }
                    datePickerField = null
                }) { Text(stringResource(Res.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { datePickerField = null }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomFieldInput(
    def: CustomFieldDefinitionSchema,
    customFields: CustomFieldValues,
    onUpdate: (CustomFieldValues) -> Unit,
    enabled: Boolean,
    onRequestDatePicker: () -> Unit,
) {
    when (def.fieldType) {
        "text", "phone", "email", "url" -> {
            val value = (customFields[def.fieldKey] as? CustomFieldValue.Text)?.value ?: ""
            OutlinedTextField(
                value = value,
                onValueChange = { newValue ->
                    val updated =
                        customFields.with(CustomFieldValue.Text(def.fieldKey, newValue))
                            .fold({ customFields }, { it })
                    onUpdate(updated)
                },
                label = { Text(def.label) },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        "number" -> {
            val value = (customFields[def.fieldKey] as? CustomFieldValue.Number)?.value?.toString() ?: ""
            OutlinedTextField(
                value = value,
                onValueChange = { newValue ->
                    val parsed = newValue.toDoubleOrNull() ?: return@OutlinedTextField
                    val updated =
                        customFields.with(CustomFieldValue.Number(def.fieldKey, parsed))
                            .fold({ customFields }, { it })
                    onUpdate(updated)
                },
                label = { Text(def.label) },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        "boolean" -> {
            val value = (customFields[def.fieldKey] as? CustomFieldValue.Bool)?.value ?: false
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = def.label,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = value,
                    onCheckedChange = { checked ->
                        val updated =
                            customFields.with(CustomFieldValue.Bool(def.fieldKey, checked))
                                .fold({ customFields }, { it })
                        onUpdate(updated)
                    },
                    enabled = enabled,
                )
            }
        }

        "date" -> {
            val value = (customFields[def.fieldKey] as? CustomFieldValue.Date)?.value
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = value?.toString() ?: "",
                    onValueChange = {},
                    label = { Text(def.label) },
                    placeholder = { Text(stringResource(Res.string.hint_date_format)) },
                    singleLine = true,
                    readOnly = true,
                    enabled = enabled,
                    trailingIcon = {
                        if (value != null) {
                            TextButton(onClick = {
                                val newRaw = customFields.toList().filterNot { it.fieldKey == def.fieldKey }
                                onUpdate(CustomFieldValues(customFields.definitions).with(newRaw).fold({ customFields }, { it }))
                            }) {
                                Text(stringResource(Res.string.action_clear))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .clickable(enabled = enabled) { onRequestDatePicker() },
                )
            }
        }

        "select" -> {
            val config = def.config.toTypeConfig("select") as? CustomFieldTypeConfig.SelectConfig
            val options = config?.options ?: emptyList()
            val currentValue = (customFields[def.fieldKey] as? CustomFieldValue.Select)?.value ?: ""
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = currentValue,
                    onValueChange = {},
                    label = { Text(def.label) },
                    placeholder = { Text(stringResource(Res.string.hint_select_option)) },
                    singleLine = true,
                    readOnly = true,
                    enabled = enabled,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier =
                        Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                val updated =
                                    customFields.with(CustomFieldValue.Select(def.fieldKey, option))
                                        .fold({ customFields }, { it })
                                onUpdate(updated)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}
