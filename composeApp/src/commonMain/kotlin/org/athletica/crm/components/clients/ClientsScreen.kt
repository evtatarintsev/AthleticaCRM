package org.athletica.crm.components.clients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.clients.ClientListItem
import org.athletica.crm.api.schemas.clients.ClientListRequest
import org.athletica.crm.api.schemas.clients.CreateClientRequest
import kotlin.uuid.Uuid

/**
 * Экран списка клиентов с поиском, фильтрами и выбором записей.
 * Загружает данные через [api] при первом отображении.
 * Если [showCreateDialog] равен `true` — отображает диалог создания нового клиента.
 * [onDismissCreateDialog] вызывается при закрытии диалога.
 */
@Composable
fun ClientsScreen(
    api: ApiClient,
    showCreateDialog: Boolean = false,
    onDismissCreateDialog: () -> Unit = {},
    onClientClick: (ClientListItem) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var clients by remember { mutableStateOf<List<ClientListItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedIds by remember { mutableStateOf<Set<Uuid>>(emptySet()) }
    var filter by remember { mutableStateOf(ClientFilterState()) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(ClientDisplaySettings()) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    var newClientName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
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

    LaunchedEffect(showCreateDialog) {
        if (!showCreateDialog) {
            newClientName = ""
            createError = null
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { if (!isCreating) onDismissCreateDialog() },
            title = { Text("Новый клиент") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = newClientName,
                        onValueChange = { newClientName = it },
                        label = { Text("Имя") },
                        singleLine = true,
                        isError = createError != null,
                        enabled = !isCreating,
                    )
                    if (createError != null) {
                        Text(
                            text = createError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            isCreating = true
                            createError = null
                            api
                                .createClient(CreateClientRequest(id = Uuid.generateV7(), name = newClientName))
                                .fold(
                                    ifLeft = { err ->
                                        createError =
                                            when (err) {
                                                is ApiClientError.Unauthenticated -> "Сессия истекла"
                                                is ApiClientError.ValidationError -> err.message
                                                is ApiClientError.Unavailable -> "Сервис недоступен"
                                            }
                                        isCreating = false
                                    },
                                    ifRight = { created ->
                                        clients = clients + ClientListItem(id = created.id, name = created.name)
                                        isCreating = false
                                        onDismissCreateDialog()
                                    },
                                )
                        }
                    },
                    enabled = newClientName.isNotBlank() && !isCreating,
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text("Создать")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissCreateDialog, enabled = !isCreating) {
                    Text("Отмена")
                }
            },
        )
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
                val filteredClients = filter.applyTo(clients).take(settings.pageSize)

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
                                Text("Клиентов пока нет", style = MaterialTheme.typography.bodyLarge)
                            }

                        filteredClients.isEmpty() ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Ничего не найдено", style = MaterialTheme.typography.bodyLarge)
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
                                        settings = settings,
                                        selected = client.id in selectedIds,
                                        onClick = { onClientClick(client) },
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
                        onDelete = { selectedIds = emptySet() },
                        onNotify = {},
                        onExport = {},
                        modifier = Modifier.align(Alignment.BottomCenter),
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
    onDelete: () -> Unit,
    onNotify: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BottomAppBar(modifier = modifier) {
        Text(
            text = "Выбрано: $selectedCount",
            style = MaterialTheme.typography.titleSmall,
            modifier =
                Modifier
                    .padding(start = 16.dp)
                    .weight(1f),
        )
        IconButton(onClick = onDelete) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = "Удалить выбранных")
        }
        IconButton(onClick = onNotify) {
            Icon(imageVector = Icons.Default.Notifications, contentDescription = "Оповестить выбранных")
        }
        IconButton(onClick = onExport) {
            Icon(imageVector = Icons.Default.Share, contentDescription = "Экспортировать выбранных")
        }
    }
}
