package org.athletica.crm.components.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.schemas.groups.ScheduleSlot

private val DAY_NAMES = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

/**
 * Редактор расписания группы.
 * Отображает список слотов и позволяет добавлять, изменять и удалять их.
 * В один день может быть несколько слотов.
 */
@Composable
fun ScheduleEditor(
    slots: List<ScheduleSlot>,
    onSlotsChange: (List<ScheduleSlot>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (slots.isEmpty()) {
            Text(
                text = "Расписание не задано",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        slots.forEachIndexed { index, slot ->
            ScheduleSlotRow(
                slot = slot,
                onSlotChange = { updated ->
                    onSlotsChange(slots.toMutableList().also { it[index] = updated })
                },
                onDelete = {
                    onSlotsChange(slots.toMutableList().also { it.removeAt(index) })
                },
            )
        }

        TextButton(
            onClick = {
                onSlotsChange(slots + ScheduleSlot(dayOfWeek = 0, startAt = "", endAt = ""))
            },
            modifier = Modifier.align(Alignment.Start),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Добавить слот")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleSlotRow(
    slot: ScheduleSlot,
    onSlotChange: (ScheduleSlot) -> Unit,
    onDelete: () -> Unit,
) {
    var dayExpanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ExposedDropdownMenuBox(
            expanded = dayExpanded,
            onExpandedChange = { dayExpanded = it },
            modifier = Modifier.width(92.dp),
        ) {
            OutlinedTextField(
                value = DAY_NAMES[slot.dayOfWeek],
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dayExpanded) },
                modifier =
                    Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .width(92.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            )
            ExposedDropdownMenu(
                expanded = dayExpanded,
                onDismissRequest = { dayExpanded = false },
            ) {
                DAY_NAMES.forEachIndexed { index, name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            onSlotChange(slot.copy(dayOfWeek = index))
                            dayExpanded = false
                        },
                    )
                }
            }
        }

        TimeTextField(
            value = slot.startAt,
            onValueChange = { onSlotChange(slot.copy(startAt = it)) },
        )

        Text(
            text = "—",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TimeTextField(
            value = slot.endAt,
            onValueChange = { onSlotChange(slot.copy(endAt = it)) },
        )

        Spacer(Modifier.weight(1f))

        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Удалить слот",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TimeTextField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val isValid = value.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$"))
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            val filtered = input.filter { it.isDigit() || it == ':' }
            if (filtered.length <= 5) onValueChange(filtered)
        },
        isError = value.isNotEmpty() && !isValid,
        placeholder = { Text("00:00", style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.width(90.dp),
    )
}
