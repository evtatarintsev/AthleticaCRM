package org.athletica.crm.components.clients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Панель расширенных фильтров в виде [ModalBottomSheet].
 * Изменения применяются немедленно через [onFilterChange].
 * Кнопка «Сбросить все» возвращает [ClientFilterState] к значениям по умолчанию.
 *
 * [filter] — текущее состояние фильтров.
 * [onFilterChange] — вызывается при любом изменении фильтра.
 * [onDismiss] — вызывается при закрытии шита.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientsFilterSheet(
    filter: ClientFilterState,
    onFilterChange: (ClientFilterState) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Фильтры",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                if (filter.chipCount > 0) {
                    TextButton(onClick = { onFilterChange(ClientFilterState(nameQuery = filter.nameQuery)) }) {
                        Text("Сбросить все")
                    }
                }
            }

            HorizontalDivider()

            // Пол
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Пол", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    GenderFilter.entries.forEachIndexed { index, g ->
                        SegmentedButton(
                            selected = filter.gender == g,
                            onClick = { onFilterChange(filter.copy(gender = g)) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = GenderFilter.entries.size,
                            ),
                        ) {
                            Text(g.label)
                        }
                    }
                }
            }

            // Год рождения
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Год рождения", style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = filter.birthYearFrom?.toString() ?: "",
                        onValueChange = { onFilterChange(filter.copy(birthYearFrom = it.toIntOrNull())) },
                        label = { Text("С") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = filter.birthYearTo?.toString() ?: "",
                        onValueChange = { onFilterChange(filter.copy(birthYearTo = it.toIntOrNull())) },
                        label = { Text("По") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Без группы
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Без группы",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = filter.noGroupOnly,
                    onCheckedChange = { onFilterChange(filter.copy(noGroupOnly = it)) },
                )
            }

            // Есть задолженность
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Есть задолженность",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = filter.hasDebtOnly,
                    onCheckedChange = { onFilterChange(filter.copy(hasDebtOnly = it)) },
                )
            }
        }
    }
}
