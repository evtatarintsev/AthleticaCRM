package org.athletica.crm.ui.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.list_sort_ascending
import org.athletica.crm.generated.resources.list_sort_descending
import org.athletica.crm.generated.resources.list_sort_reset
import org.athletica.crm.generated.resources.list_sort_title
import org.jetbrains.compose.resources.stringResource

/**
 * Описание сортируемой колонки для диалога сортировки.
 * [id] — соответствует [ListColumn.id].
 * [label] — отображаемое имя.
 */
data class SortableColumn(
    val id: ColumnId,
    val label: String,
)

/**
 * ModalBottomSheet выбора колонки и направления сортировки.
 * Открывается на COMPACT, где заголовков таблицы нет.
 *
 * [columns] — список сортируемых колонок (минимум 1).
 * [current] — текущее состояние сортировки (`null` — сортировка не выбрана).
 * [onSortChange] — выбрана новая сортировка или `null` для сброса.
 * [onDismiss] — закрыть диалог.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortBottomSheet(
    columns: List<SortableColumn>,
    current: SortState?,
    onSortChange: (SortState?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = stringResource(Res.string.list_sort_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            HorizontalDivider()
            columns.forEach { column ->
                val isActive = current?.columnId == column.id
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = isActive,
                        onClick = {
                            onSortChange(SortState(column.id, SortDirection.Asc))
                        },
                    )
                    Text(
                        text = column.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (isActive) {
                    SingleChoiceSegmentedButtonRow(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        SegmentedButton(
                            selected = current.direction == SortDirection.Asc,
                            onClick = { onSortChange(SortState(column.id, SortDirection.Asc)) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        ) {
                            Text(stringResource(Res.string.list_sort_ascending))
                        }
                        SegmentedButton(
                            selected = current.direction == SortDirection.Desc,
                            onClick = { onSortChange(SortState(column.id, SortDirection.Desc)) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        ) {
                            Text(stringResource(Res.string.list_sort_descending))
                        }
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { onSortChange(null) },
                    enabled = current != null,
                ) {
                    Text(stringResource(Res.string.list_sort_reset))
                }
            }
        }
    }
}
