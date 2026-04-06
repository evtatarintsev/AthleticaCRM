package org.athletica.crm.components.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.groups.GroupListItem
import org.athletica.crm.api.schemas.groups.GroupListRequest
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_add_group
import org.athletica.crm.generated.resources.action_clear_search
import org.athletica.crm.generated.resources.action_delete_selected
import org.athletica.crm.generated.resources.action_notify_selected
import org.athletica.crm.generated.resources.empty_search_results
import org.athletica.crm.generated.resources.groups_empty
import org.athletica.crm.generated.resources.hint_search_by_title
import org.athletica.crm.generated.resources.label_selected_count
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

/**
 * Экран списка групп организации с поиском, чекбоксами и меню действий.
 * [refreshKey] — при изменении перезагружает список (например, после создания группы).
 * [onNavigateToCreate] — переход к экрану создания группы.
 */
@Composable
fun GroupsScreen(
    api: ApiClient,
    onNavigateToCreate: () -> Unit,
    refreshKey: Int = 0,
    modifier: Modifier = Modifier,
) {
    var groups by remember { mutableStateOf<List<GroupListItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(GroupFilterState()) }
    var selectedIds by remember { mutableStateOf<Set<Uuid>>(emptySet()) }

    LaunchedEffect(refreshKey) {
        isLoading = true
        api
            .groupList(GroupListRequest())
            .fold(
                ifLeft = { err ->
                    error =
                        when (err) {
                            is ApiClientError.Unauthenticated -> "Сессия истекла"
                            is ApiClientError.ValidationError -> err.message
                            is ApiClientError.Unavailable -> "Сервис недоступен. Проверьте соединение"
                        }
                },
                ifRight = { response -> groups = response.groups },
            )
        isLoading = false
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (selectedIds.isEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onNavigateToCreate,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(Res.string.action_add_group)) },
                )
            }
        },
        bottomBar = {
            if (selectedIds.isNotEmpty()) {
                GroupsBottomActionBar(
                    selectedCount = selectedIds.size,
                    onDelete = { selectedIds = emptySet() },
                    onNotify = {},
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                isLoading ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                error != null ->
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.Center),
                    )

                else -> {
                    val filteredGroups = filter.applyTo(groups)

                    Column(Modifier.fillMaxSize()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            OutlinedTextField(
                                value = filter.nameQuery,
                                onValueChange = { filter = filter.copy(nameQuery = it) },
                                placeholder = {
                                    Text(
                                        stringResource(Res.string.hint_search_by_title),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                trailingIcon = {
                                    if (filter.nameQuery.isNotBlank()) {
                                        IconButton(onClick = { filter = filter.copy(nameQuery = "") }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = stringResource(Res.string.action_clear_search),
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        HorizontalDivider()

                        when {
                            groups.isEmpty() ->
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(stringResource(Res.string.groups_empty), style = MaterialTheme.typography.bodyLarge)
                                }

                            filteredGroups.isEmpty() ->
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(stringResource(Res.string.empty_search_results), style = MaterialTheme.typography.bodyLarge)
                                }

                            else ->
                                LazyColumn(
                                    contentPadding =
                                        PaddingValues(
                                            top = 4.dp,
                                            bottom = if (selectedIds.isNotEmpty()) 80.dp else 4.dp,
                                        ),
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    items(filteredGroups, key = { it.id }) { group ->
                                        GroupRow(
                                            group = group,
                                            selected = group.id in selectedIds,
                                            onCheckedChange = { checked ->
                                                selectedIds =
                                                    if (checked) {
                                                        selectedIds + group.id
                                                    } else {
                                                        selectedIds - group.id
                                                    }
                                            },
                                        )
                                        HorizontalDivider()
                                    }
                                }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Нижняя панель действий при выбранных группах.
 */
@Composable
private fun GroupsBottomActionBar(
    selectedCount: Int,
    onDelete: () -> Unit,
    onNotify: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BottomAppBar(modifier = modifier) {
        Text(
            text = stringResource(Res.string.label_selected_count, selectedCount),
            style = MaterialTheme.typography.titleSmall,
            modifier =
                Modifier
                    .padding(start = 16.dp)
                    .weight(1f),
        )
        IconButton(onClick = onDelete) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = stringResource(Res.string.action_delete_selected))
        }
        IconButton(onClick = onNotify) {
            Icon(imageVector = Icons.Default.Notifications, contentDescription = stringResource(Res.string.action_notify_selected))
        }
    }
}
