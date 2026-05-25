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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.schemas.groups.GroupListItem
import org.athletica.crm.components.avatar.TextAvatar

/**
 * Мобильное представление группы (карточка) для COMPACT-режима.
 * Отображает аватар-плейсхолдер, название группы, тренеров и расписание.
 *
 * [group] — данные группы.
 * [isSelected] — признак выбора строки.
 * [onToggleSelect] — переключить выбор.
 * [onClick] — переход к карточке группы.
 */
@Composable
fun GroupMobileItem(
    group: GroupListItem,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
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
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val coachesText = group.employees.joinToString(", ") { it.name }
                if (coachesText.isNotEmpty()) {
                    Text(
                        text = coachesText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val scheduleText = formatScheduleSummary(group)
                if (scheduleText.isNotEmpty()) {
                    Text(
                        text = scheduleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
            )
        }
        HorizontalDivider()
    }
}

/** Форматирует расписание группы в строку вида «Пн 19:00, Ср 20:00». */
internal fun formatScheduleSummary(group: GroupListItem): String =
    group.schedule.joinToString(", ") { slot ->
        val hour = slot.startAt.hour.toString().padStart(2, '0')
        val minute = slot.startAt.minute.toString().padStart(2, '0')
        "${slot.dayOfWeek.displayName} $hour:$minute"
    }
