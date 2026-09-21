package org.athletica.crm.components.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_cancel
import org.athletica.crm.generated.resources.action_save
import org.athletica.crm.generated.resources.schedule_effective_from_hint
import org.athletica.crm.generated.resources.schedule_effective_from_invalid
import org.athletica.crm.generated.resources.schedule_effective_from_label
import org.athletica.crm.generated.resources.schedule_planned_change_cancelled
import org.athletica.crm.generated.resources.schedule_set_title
import org.jetbrains.compose.resources.stringResource

/**
 * Диалог установки расписания группы с датой вступления в силу.
 *
 * Расписание задаётся целиком: набор слотов заменяет всё, что действует начиная
 * с указанной даты. Если это отменяет ранее запланированное изменение, диалог предупреждает об этом.
 */
@Composable
fun SetScheduleDialog(
    viewModel: SetScheduleViewModel,
    onDismiss: () -> Unit,
) {
    val state = viewModel.state
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.schedule_set_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.effectiveFromText,
                    onValueChange = viewModel::onEffectiveFromChange,
                    label = { Text(stringResource(Res.string.schedule_effective_from_label)) },
                    placeholder = { Text(stringResource(Res.string.schedule_effective_from_hint)) },
                    isError = state.isDateInvalid,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.isDateInvalid) {
                    Text(
                        text = stringResource(Res.string.schedule_effective_from_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (state.cancelsPlannedChange) {
                    Text(
                        text =
                            stringResource(
                                Res.string.schedule_planned_change_cancelled,
                                state.plannedChangeAt.toString(),
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                ScheduleEditor(
                    slots = state.slots,
                    halls = state.halls,
                    onSlotsChange = viewModel::onSlotsChange,
                )

                state.error?.let { error ->
                    Text(
                        text = error.message(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::onSave, enabled = state.isValid) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.fillMaxWidth(0.1f))
                } else {
                    Text(stringResource(Res.string.action_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isSaving) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}
