package org.athletica.crm.components.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.core.tasks.TaskStatus
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_attach_file
import org.athletica.crm.generated.resources.label_task_assignee
import org.athletica.crm.generated.resources.label_task_attachments
import org.athletica.crm.generated.resources.label_task_client
import org.athletica.crm.generated.resources.label_task_description
import org.athletica.crm.generated.resources.label_task_due_date
import org.athletica.crm.generated.resources.label_task_due_date_end
import org.athletica.crm.generated.resources.task_assignee_unassigned
import org.athletica.crm.generated.resources.task_status_completed
import org.athletica.crm.generated.resources.task_status_in_progress
import org.athletica.crm.generated.resources.task_status_paused
import org.athletica.crm.generated.resources.task_status_pending
import org.jetbrains.compose.resources.stringResource

/**
 * Экран детального просмотра задачи.
 * Позволяет просматривать детали задачи и менять статус.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    taskId: TaskId,
    api: ApiClient,
    onBack: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { TaskDetailViewModel(taskId, api, scope) }
    var showStatusMenu by remember { mutableStateOf(false) }
    var showAssigneeSheet by remember { mutableStateOf(false) }

    LaunchedEffect(taskId) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val s = viewModel.state
                    if (s is TaskDetailState.Loaded) {
                        Text(s.task.title)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        when (val s = viewModel.state) {
            is TaskDetailState.Loading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is TaskDetailState.Error -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Ошибка загрузки задачи")
                }
            }

            is TaskDetailState.Loaded -> {
                val task = s.task
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TaskStatusBadge(status = task.status)

                        Box {
                            TextButton(onClick = { showStatusMenu = true }) {
                                Text("Сменить статус")
                            }
                            DropdownMenu(
                                expanded = showStatusMenu,
                                onDismissRequest = { showStatusMenu = false },
                            ) {
                                TaskStatus.entries.forEach { status ->
                                    DropdownMenuItem(
                                        text = { Text(status.label()) },
                                        onClick = {
                                            showStatusMenu = false
                                            viewModel.changeStatus(status)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    if (task.description.isNotBlank()) {
                        Text(
                            text = stringResource(Res.string.label_task_description),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(text = task.description, style = MaterialTheme.typography.bodyMedium)
                    }

                    ClickableField(
                        label = stringResource(Res.string.label_task_assignee),
                        value = task.assigneeName ?: stringResource(Res.string.task_assignee_unassigned),
                        onClick = { showAssigneeSheet = true },
                    )

                    task.clientName?.let { name ->
                        Text(
                            text = stringResource(Res.string.label_task_client),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(text = name, style = MaterialTheme.typography.bodyMedium)
                    }

                    TaskDateField(
                        label = stringResource(Res.string.label_task_due_date),
                        value = task.dueDate,
                        onChange = { viewModel.updateDates(it, task.dueDateEnd) },
                    )

                    TaskDateField(
                        label = stringResource(Res.string.label_task_due_date_end),
                        value = task.dueDateEnd,
                        onChange = { viewModel.updateDates(task.dueDate, it) },
                    )

                    Text(
                        text = stringResource(Res.string.label_task_attachments),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    task.attachments.forEach { attachment ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = attachment.originalName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { viewModel.detachFile(attachment.id) }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { viewModel.attachFile() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(Res.string.action_attach_file))
                    }
                }
            }
        }
    }

    if (showAssigneeSheet) {
        PickAssigneeSheet(
            api = api,
            onDismiss = { showAssigneeSheet = false },
            onPicked = { employee ->
                showAssigneeSheet = false
                viewModel.setAssignee(employee?.id)
            },
        )
    }
}

@Composable
private fun TaskStatus.label(): String =
    when (this) {
        TaskStatus.PENDING -> stringResource(Res.string.task_status_pending)
        TaskStatus.IN_PROGRESS -> stringResource(Res.string.task_status_in_progress)
        TaskStatus.PAUSED -> stringResource(Res.string.task_status_paused)
        TaskStatus.COMPLETED -> stringResource(Res.string.task_status_completed)
    }
