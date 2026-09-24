package org.athletica.crm.components.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_cancel
import org.athletica.crm.generated.resources.action_ok
import org.athletica.crm.generated.resources.action_save
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
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                EffectiveFromField(
                    value = state.effectiveFrom,
                    minDate = state.today,
                    enabled = !state.isSaving,
                    onChange = viewModel::onEffectiveFromChange,
                )

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
                    cards = state.cards,
                    halls = state.halls,
                    cardErrors = state.cardErrors,
                    onCardAdd = viewModel::onCardAdd,
                    onCardChange = viewModel::onCardChange,
                    onCardRemove = viewModel::onCardRemove,
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

/**
 * Поле даты вступления расписания в силу со значением [value].
 * По клику открывает календарь, в котором нельзя выбрать дату раньше [minDate];
 * выбранную дату передаёт в [onChange]. Неактивно, если [enabled] ложно.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EffectiveFromField(
    value: LocalDate,
    minDate: LocalDate,
    enabled: Boolean,
    onChange: (LocalDate) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value.toString(),
            onValueChange = {},
            label = { Text(stringResource(Res.string.schedule_effective_from_label)) },
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .clickable(enabled = enabled) { showDatePicker = true },
        )
    }

    if (showDatePicker) {
        val minDays = minDate.toEpochDays()
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = value.toEpochDays() * MILLIS_PER_DAY,
                selectableDates =
                    object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis / MILLIS_PER_DAY >= minDays

                        override fun isSelectableYear(year: Int) = year >= minDate.year
                    },
            )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onChange(LocalDate.fromEpochDays(millis / MILLIS_PER_DAY))
                    }
                    showDatePicker = false
                }) { Text(stringResource(Res.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(Res.string.action_cancel)) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private const val MILLIS_PER_DAY = 86_400_000L
