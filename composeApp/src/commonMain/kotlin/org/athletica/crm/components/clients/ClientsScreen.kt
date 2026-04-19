package org.athletica.crm.components.clients

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.clients.ClientListItem
import org.athletica.crm.api.schemas.clients.ClientListRequest
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_add_client_group
import org.athletica.crm.generated.resources.action_delete_selected
import org.athletica.crm.generated.resources.action_export_selected
import org.athletica.crm.generated.resources.action_notify_selected
import org.athletica.crm.generated.resources.clients_empty
import org.athletica.crm.generated.resources.empty_search_results
import org.athletica.crm.generated.resources.label_selected_count
import org.jetbrains.compose.resources.stringResource

/**
 * Экран списка клиентов с поиском, фильтрами и выбором записей.
 * [refreshKey] — при изменении перезагружает список (например, после создания клиента).
 * [onNavigateToCreate] — переход к экрану создания клиента.
 */
@Composable
fun ClientsScreen(
    api: ApiClient,
    onNavigateToCreate: () -> Unit = {},
    refreshKey: Int = 0,
    onClientClick: (ClientId) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var clients by remember { mutableStateOf<List<ClientListItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedIds by remember { mutableStateOf<Set<ClientId>>(emptySet()) }
    var filter by remember { mutableStateOf(ClientFilterState()) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showAddToGroupSheet by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(ClientDisplaySettings()) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        isLoading = true
        api
            .clientList(ClientListRequest())
            .fold(
                ifLeft = { err ->
                    error =
                        when (err) {
                            is ApiClientError.Unauthenticated -> "Сессия истекла"
                            is ApiClientError.ValidationError -> err.message
                            is ApiClientError.Unavailable -> "Сервис недоступен. Проверьте соединение"
                        }
                },
                ifRight = { response -> clients = response.clients },
            )
        isLoading = false
    }

    if (showSettingsDialog) {
        ClientsSettingsDialog(
            settings = settings,
            onSettingsChange = { settings = it },
            onDismiss = { showSettingsDialog = false },
        )
    }

    if (showFilterSheet) {
        ClientsFilterSheet(
            filter = filter,
            onFilterChange = { filter = it },
            onDismiss = { showFilterSheet = false },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
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
                val filteredClients = filter.applyTo(clients)

                val selectAllState =
                    when {
                        selectedIds.isEmpty() -> ToggleableState.Off
                        selectedIds.containsAll(filteredClients.map { it.id }) -> ToggleableState.On
                        else -> ToggleableState.Indeterminate
                    }

                Column(Modifier.fillMaxSize()) {
                    ClientsFilterBar(
                        filter = filter,
                        onFilterChange = { filter = it },
                        onOpenSheet = { showFilterSheet = true },
                        onOpenSettings = { showSettingsDialog = true },
                    )

                    when {
                        clients.isEmpty() ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(Res.string.clients_empty), style = MaterialTheme.typography.bodyLarge)
                            }

                        filteredClients.isEmpty() ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(Res.string.empty_search_results), style = MaterialTheme.typography.bodyLarge)
                            }

                        else -> {
                            ClientsTableHeader(
                                selectAllState = selectAllState,
                                settings = settings,
                                onSelectAllClick = {
                                    selectedIds =
                                        if (selectAllState == ToggleableState.On) {
                                            emptySet()
                                        } else {
                                            filteredClients.map { it.id }.toSet()
                                        }
                                },
                            )
                            HorizontalDivider()
                            LazyColumn(
                                contentPadding =
                                    PaddingValues(
                                        top = 4.dp,
                                        bottom = if (selectedIds.isNotEmpty()) 80.dp else 4.dp,
                                    ),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(filteredClients, key = { it.id }) { client ->
                                    ClientRow(
                                        client = client,
                                        api = api,
                                        settings = settings,
                                        selected = client.id in selectedIds,
                                        onClick = { onClientClick(client.id) },
                                        onCheckedChange = { checked ->
                                            selectedIds =
                                                if (checked) {
                                                    selectedIds + client.id
                                                } else {
                                                    selectedIds - client.id
                                                }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                if (selectedIds.isNotEmpty()) {
                    ClientsBottomActionBar(
                        selectedCount = selectedIds.size,
                        onAddToGroup = { showAddToGroupSheet = true },
                        onDelete = { selectedIds = emptySet() },
                        onNotify = {},
                        onExport = {},
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }

                if (showAddToGroupSheet && selectedIds.isNotEmpty()) {
                    AddToGroupSheet(
                        clientIds = selectedIds.toList(),
                        api = api,
                        onDismiss = { showAddToGroupSheet = false },
                        onGroupAdded = {
                            showAddToGroupSheet = false
                            selectedIds = emptySet()
                        },
                    )
                }
            }
        }
    }
}

/**
 * Контекстная нижняя панель действий, появляющаяся при выборе клиентов.
 * [selectedCount] — количество выбранных записей.
 * [onDelete], [onNotify], [onExport] — обработчики массовых действий.
 */
@Composable
private fun ClientsBottomActionBar(
    selectedCount: Int,
    onAddToGroup: () -> Unit,
    onDelete: () -> Unit,
    onNotify: () -> Unit,
    onExport: () -> Unit,
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
        IconButton(onClick = onAddToGroup) {
            Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(Res.string.action_add_client_group))
        }
        IconButton(onClick = onDelete) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = stringResource(Res.string.action_delete_selected))
        }
        IconButton(onClick = onNotify) {
            Icon(imageVector = Icons.Default.Notifications, contentDescription = stringResource(Res.string.action_notify_selected))
        }
        IconButton(onClick = onExport) {
            Icon(imageVector = Icons.Default.Share, contentDescription = stringResource(Res.string.action_export_selected))
        }
    }
}
