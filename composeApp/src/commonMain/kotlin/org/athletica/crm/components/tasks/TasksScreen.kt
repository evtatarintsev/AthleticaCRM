package org.athletica.crm.components.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_create_task
import org.athletica.crm.generated.resources.tasks_filter_mine
import org.athletica.crm.generated.resources.tasks_title
import org.jetbrains.compose.resources.stringResource

/**
 * Экран списка задач с быстрыми фильтрами и кнопкой создания.
 * [onNavigateToCreate] — переход к экрану создания задачи.
 * [onTaskClick] — переход к карточке задачи.
 */
@Composable
fun TasksScreen(
    api: ApiClient,
    onNavigateToCreate: () -> Unit = {},
    onTaskClick: (TaskId) -> Unit = {},
    refreshKey: Int = 0,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { TasksViewModel(api, scope) }

    LaunchedEffect(refreshKey) { viewModel.load() }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToCreate,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(Res.string.action_create_task)) },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Text(
                text = stringResource(Res.string.tasks_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                FilterChip(
                    selected = viewModel.filters.onlyMine,
                    onClick = {
                        viewModel.updateFilters(viewModel.filters.copy(onlyMine = !viewModel.filters.onlyMine))
                    },
                    label = { Text(stringResource(Res.string.tasks_filter_mine)) },
                )
            }

            when (val s = viewModel.state) {
                is TasksState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is TasksState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Ошибка загрузки задач")
                    }
                }

                is TasksState.Loaded -> {
                    if (s.tasks.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Нет задач")
                        }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                            items(s.tasks, key = { it.id.value }) { task ->
                                TaskListItemCard(
                                    task = task,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { onTaskClick(task.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
