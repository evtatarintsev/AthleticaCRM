package org.athletica.crm.components.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.athletica.crm.api.schemas.tasks.TaskListItemSchema
import kotlin.time.Instant

/**
 * Мобильное представление задачи (карточка) для COMPACT-режима.
 * Отображает заголовок, исполнителя, клиента, срок и статус.
 *
 * [task] — данные задачи.
 * [isSelected] — признак выбора (не используется в Этапе 2, зарезервирован).
 * [onToggleSelect] — переключить выбор (не используется в Этапе 2).
 * [onClick] — клик по карточке для перехода к деталям.
 */
@Composable
fun TaskMobileItem(
    task: TaskListItemSchema,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta =
                    buildString {
                        task.assigneeName?.let { append(it) }
                        task.clientName?.let {
                            if (isNotEmpty()) {
                                append(" · ")
                            }
                            append(it)
                        }
                    }
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                task.dueDate?.let { due ->
                    Text(
                        text = formatDueDate(due),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            TaskStatusBadge(status = task.status)
        }
        HorizontalDivider()
    }
}

/** Форматирует [Instant] в строку «дд.мм чч:мм» по системному часовому поясу. */
fun formatDueDate(instant: Instant): String {
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val day = local.day.toString().padStart(2, '0')
    val month = local.month.number.toString().padStart(2, '0')
    val hour = local.hour.toString().padStart(2, '0')
    val minute = local.minute.toString().padStart(2, '0')
    return "$day.$month $hour:$minute"
}
