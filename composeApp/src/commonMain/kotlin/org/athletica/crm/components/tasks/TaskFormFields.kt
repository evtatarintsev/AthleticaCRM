package org.athletica.crm.components.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_cancel
import org.athletica.crm.generated.resources.action_clear
import org.athletica.crm.generated.resources.action_ok
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * Кликабельное read-only поле с подписью [label] и значением [value].
 * По клику вызывает [onClick] (например, открытие шторки выбора исполнителя).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClickableField(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(modifier = Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

/**
 * Поле выбора даты [value] с подписью [label] и кнопкой очистки.
 * При выборе вызывает [onChange] с моментом полуночи UTC выбранной даты, либо null при очистке.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskDateField(
    label: String,
    value: Instant?,
    onChange: (Instant?) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value?.let { formatDueDate(it) } ?: "",
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            singleLine = true,
            trailingIcon = {
                if (value != null) {
                    TextButton(onClick = { onChange(null) }) {
                        Text(stringResource(Res.string.action_clear))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(modifier = Modifier.matchParentSize().clickable { showDialog = true })
    }

    if (showDialog) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onChange(Instant.fromEpochMilliseconds(millis))
                    }
                    showDialog = false
                }) { Text(stringResource(Res.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
