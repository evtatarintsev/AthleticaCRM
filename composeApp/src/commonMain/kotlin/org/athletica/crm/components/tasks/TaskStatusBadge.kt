package org.athletica.crm.components.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.athletica.crm.core.tasks.TaskStatus
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.task_status_completed
import org.athletica.crm.generated.resources.task_status_in_progress
import org.athletica.crm.generated.resources.task_status_paused
import org.athletica.crm.generated.resources.task_status_pending
import org.jetbrains.compose.resources.stringResource

/** Визуальный бейдж со статусом задачи. */
@Composable
fun TaskStatusBadge(
    status: TaskStatus,
    modifier: Modifier = Modifier,
) {
    val (label, color) =
        when (status) {
            TaskStatus.PENDING -> stringResource(Res.string.task_status_pending) to Color(0xFF9E9E9E)
            TaskStatus.IN_PROGRESS -> stringResource(Res.string.task_status_in_progress) to Color(0xFF1976D2)
            TaskStatus.PAUSED -> stringResource(Res.string.task_status_paused) to Color(0xFFF57C00)
            TaskStatus.COMPLETED -> stringResource(Res.string.task_status_completed) to Color(0xFF388E3C)
        }

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier =
            modifier
                .background(color, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
