package org.athletica.crm.components.tasks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.athletica.crm.core.tasks.TaskStatus
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.task_status_completed
import org.athletica.crm.generated.resources.task_status_in_progress
import org.athletica.crm.generated.resources.task_status_paused
import org.athletica.crm.generated.resources.task_status_pending
import org.athletica.crm.generated.resources.tasks_filter_only_mine
import org.athletica.crm.generated.resources.tasks_filter_section_assignee
import org.athletica.crm.generated.resources.tasks_filter_section_status
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Содержимое панели фильтров задач.
 * Используется как слот внутри [org.athletica.crm.ui.list.ListPageFilterPanel].
 * Контейнер (заголовок, Reset, Apply) предоставляется scaffold-ом.
 *
 * [draft] — черновик фильтра; изменяется без перезагрузки списка.
 * [onDraftChange] — обновить черновик.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasksFilterContent(
    draft: TasksFilter,
    onDraftChange: (TasksFilter) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.tasks_filter_section_assignee),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(Res.string.tasks_filter_only_mine),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = draft.onlyMine,
                onCheckedChange = { onDraftChange(draft.copy(onlyMine = it)) },
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.tasks_filter_section_status),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        FlowRow(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            TaskStatus.entries.forEach { status ->
                FilterChip(
                    selected = status in draft.statuses,
                    onClick = {
                        val next =
                            if (status in draft.statuses) {
                                draft.statuses - status
                            } else {
                                draft.statuses + status
                            }
                        onDraftChange(draft.copy(statuses = next))
                    },
                    label = { Text(stringResource(statusLabelRes(status))) },
                )
            }
        }
    }
}

/** Возвращает строковый ресурс для отображаемого имени [TaskStatus]. */
fun statusLabelRes(status: TaskStatus): StringResource =
    when (status) {
        TaskStatus.PENDING -> Res.string.task_status_pending
        TaskStatus.IN_PROGRESS -> Res.string.task_status_in_progress
        TaskStatus.PAUSED -> Res.string.task_status_paused
        TaskStatus.COMPLETED -> Res.string.task_status_completed
    }
