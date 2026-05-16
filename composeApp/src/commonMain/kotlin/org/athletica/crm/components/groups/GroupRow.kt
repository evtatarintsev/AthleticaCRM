package org.athletica.crm.components.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.schemas.groups.GroupListItem
import org.athletica.crm.api.schemas.groups.ScheduleSlot
import org.athletica.crm.components.avatar.TextAvatar

/**
 * Строка группы в списке с чекбоксом и аватаром (первая буква названия).
 * Под названием отображаются преподаватели через запятую и расписание.
 * Чекбокс изолирован от кликабельной области строки.
 * [onClick] — клик по строке (не по чекбоксу).
 */
@Composable
fun GroupRow(
    group: GroupListItem,
    selected: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
        ) {
            TextAvatar(group.name)
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            val employeesText = group.employees.joinToString(", ") { it.name }
            if (employeesText.isNotEmpty()) {
                Text(
                    text = employeesText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            val scheduleText = group.schedule.toDisplayText()
            if (scheduleText.isNotEmpty()) {
                Text(
                    text = scheduleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        Checkbox(
            checked = selected,
            onCheckedChange = { checked ->
                onCheckedChange(checked)
            },
        )
    }
}

/** Форматирует расписание в строку вида «Пн 19:00, Ср 20:00». */
private fun List<ScheduleSlot>.toDisplayText(): String =
    joinToString(", ") { slot ->
        val hour = slot.startAt.hour.toString().padStart(2, '0')
        val minute = slot.startAt.minute.toString().padStart(2, '0')
        "${slot.dayOfWeek.displayName} $hour:$minute"
    }
