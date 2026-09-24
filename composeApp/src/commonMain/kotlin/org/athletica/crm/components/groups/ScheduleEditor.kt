package org.athletica.crm.components.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalTime
import org.athletica.crm.api.schemas.halls.HallDetailResponse
import org.athletica.crm.core.DayOfWeek
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_add_slot
import org.athletica.crm.generated.resources.action_delete_slot
import org.athletica.crm.generated.resources.label_hall
import org.athletica.crm.generated.resources.schedule_card_duplicate
import org.athletica.crm.generated.resources.schedule_card_end_not_after_start
import org.athletica.crm.generated.resources.schedule_card_no_days
import org.athletica.crm.generated.resources.schedule_card_no_hall
import org.athletica.crm.generated.resources.schedule_empty
import org.athletica.crm.generated.resources.schedule_halls_empty
import org.athletica.crm.generated.resources.weekday_short_friday
import org.athletica.crm.generated.resources.weekday_short_monday
import org.athletica.crm.generated.resources.weekday_short_saturday
import org.athletica.crm.generated.resources.weekday_short_sunday
import org.athletica.crm.generated.resources.weekday_short_thursday
import org.athletica.crm.generated.resources.weekday_short_tuesday
import org.athletica.crm.generated.resources.weekday_short_wednesday
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Редактор расписания группы: список карточек [cards], каждая задаёт одно занятие
 * в несколько дней недели. Зал выбирается из [halls], ошибки карточек берутся из [cardErrors].
 * Добавление, изменение и удаление карточки передаются в [onCardAdd], [onCardChange] и [onCardRemove].
 */
@Composable
fun ScheduleEditor(
    cards: List<SlotCard>,
    halls: List<HallDetailResponse>,
    cardErrors: Map<SlotCardId, List<SlotCardError>>,
    onCardAdd: () -> Unit,
    onCardChange: (SlotCard) -> Unit,
    onCardRemove: (SlotCardId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (cards.isEmpty()) {
            Text(
                text = stringResource(Res.string.schedule_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        cards.forEach { card ->
            key(card.id) {
                SlotCardEditor(
                    card = card,
                    halls = halls,
                    errors = cardErrors[card.id].orEmpty(),
                    onChange = onCardChange,
                    onRemove = { onCardRemove(card.id) },
                )
            }
        }

        TextButton(
            onClick = onCardAdd,
            modifier = Modifier.align(Alignment.Start),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(Res.string.action_add_slot))
        }

        if (halls.isEmpty()) {
            Text(
                text = stringResource(Res.string.schedule_halls_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Карточка [card] в рамке: дни недели, время, зал из [halls] и ошибки [errors] под ними.
 * Изменения карточки передаются в [onChange], удаление — в [onRemove].
 */
@Composable
private fun SlotCardEditor(
    card: SlotCard,
    halls: List<HallDetailResponse>,
    errors: List<SlotCardError>,
    onChange: (SlotCard) -> Unit,
    onRemove: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DaySelector(
                selected = card.days,
                onChange = { onChange(card.copy(days = it)) },
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TimeTextField(
                    value = card.startAt,
                    onValueChange = { onChange(card.copy(startAt = it)) },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "—",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TimeTextField(
                    value = card.endAt,
                    onValueChange = { onChange(card.copy(endAt = it)) },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.action_delete_slot),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HallSelector(
                hallId = card.hallId,
                halls = halls,
                onChange = { onChange(card.copy(hallId = it)) },
            )

            errors.forEach { error ->
                Text(
                    text = error.message(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Ряд из семи переключателей дней недели равной ширины; отмеченные дни — [selected].
 * Новый набор дней передаётся в [onChange].
 */
@Composable
private fun DaySelector(
    selected: Set<DayOfWeek>,
    onChange: (Set<DayOfWeek>) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        DayOfWeek.entries.forEach { day ->
            val isSelected = day in selected
            val shape = MaterialTheme.shapes.small
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(shape)
                        .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                            shape = shape,
                        ).toggleable(
                            value = isSelected,
                            role = Role.Checkbox,
                            onValueChange = { checked -> onChange(if (checked) selected + day else selected - day) },
                        ),
            ) {
                Text(
                    text = stringResource(day.shortName()),
                    style = MaterialTheme.typography.labelMedium,
                    color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    maxLines = 1,
                )
            }
        }
    }
}

/** Выпадающий список залов [halls] на всю ширину с выбранным [hallId]; выбор передаётся в [onChange]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HallSelector(
    hallId: HallId?,
    halls: List<HallDetailResponse>,
    onChange: (HallId) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val hallName = halls.firstOrNull { it.id == hallId }?.name ?: ""

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = hallName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(Res.string.label_hall)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            halls.forEach { hall ->
                DropdownMenuItem(
                    text = { Text(hall.name) },
                    onClick = {
                        onChange(hall.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Локализованное описание ошибки карточки. */
@Composable
private fun SlotCardError.message(): String =
    when (this) {
        SlotCardError.NoDays -> stringResource(Res.string.schedule_card_no_days)
        SlotCardError.NoHall -> stringResource(Res.string.schedule_card_no_hall)
        SlotCardError.EndNotAfterStart -> stringResource(Res.string.schedule_card_end_not_after_start)
        is SlotCardError.Duplicate ->
            stringResource(Res.string.schedule_card_duplicate, stringResource(day.shortName()), startAt.toHhMm())
    }

/** Ресурс короткого названия дня недели для переключателя. */
private fun DayOfWeek.shortName(): StringResource =
    when (this) {
        DayOfWeek.MONDAY -> Res.string.weekday_short_monday
        DayOfWeek.TUESDAY -> Res.string.weekday_short_tuesday
        DayOfWeek.WEDNESDAY -> Res.string.weekday_short_wednesday
        DayOfWeek.THURSDAY -> Res.string.weekday_short_thursday
        DayOfWeek.FRIDAY -> Res.string.weekday_short_friday
        DayOfWeek.SATURDAY -> Res.string.weekday_short_saturday
        DayOfWeek.SUNDAY -> Res.string.weekday_short_sunday
    }

private val TIME_REGEX = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

private fun LocalTime.toHhMm() = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

private fun String.toLocalTimeOrNull(): LocalTime? {
    if (!matches(TIME_REGEX)) return null
    val parts = split(":")
    return LocalTime(parts[0].toInt(), parts[1].toInt())
}

/** Поле времени в формате `ЧЧ:ММ` со значением [value]; корректное время передаётся в [onValueChange]. */
@Composable
private fun TimeTextField(
    value: LocalTime,
    onValueChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(value) { mutableStateOf(value.toHhMm()) }
    val isValid = text.matches(TIME_REGEX)
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val filtered = input.filter { it.isDigit() || it == ':' }
            if (filtered.length <= 5) {
                text = filtered
                filtered.toLocalTimeOrNull()?.let { onValueChange(it) }
            }
        },
        isError = text.isNotEmpty() && !isValid,
        placeholder = { Text("00:00", style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = modifier,
    )
}
