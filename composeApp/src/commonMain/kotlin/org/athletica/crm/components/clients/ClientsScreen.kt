package org.athletica.crm.components.clients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.clients.ClientListItem
import org.athletica.crm.api.schemas.clients.ClientListRequest
import org.athletica.crm.api.schemas.clients.CreateClientRequest
import kotlin.uuid.Uuid

/**
 * Экран списка клиентов.
 * Загружает данные через [api] при первом отображении.
 * Если [showCreateDialog] равен `true` — отображает диалог создания нового клиента.
 * [onDismissCreateDialog] вызывается при закрытии диалога.
 */
@Composable
fun ClientsScreen(
    api: ApiClient,
    showCreateDialog: Boolean = false,
    onDismissCreateDialog: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var clients by remember { mutableStateOf<List<ClientListItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedIds by remember { mutableStateOf<Set<Uuid>>(emptySet()) }

    var newClientName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        api
            .client(ClientListRequest())
            .fold(
                ifLeft = { err ->
                    error =
                        when (err) {
                            is ApiClientError.Unauthenticated -> "Сессия истекла"
                            is ApiClientError.ValidationError -> err.message
                            is ApiClientError.Unavailable -> "Сервис недоступен. Проверьте соединение"
                        }
                },
                ifRight = { response ->
                    clients = response.clients
                },
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
                TextButton(
                    onClick = onDismissCreateDialog,
                    enabled = !isCreating,
                ) {
                    Text("Отмена")
                }
            },
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            isLoading -> CircularProgressIndicator()

            error != null ->
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )

            clients.isEmpty() ->
                Text(
                    text = "Клиентов пока нет",
                    style = MaterialTheme.typography.bodyLarge,
                )

            else ->
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(clients, key = { it.id }) { client ->
                        ClientRow(
                            client = client,
                            selected = client.id in selectedIds,
                            onCheckedChange = { checked ->
                                selectedIds =
                                    if (checked) selectedIds + client.id else selectedIds - client.id
                            },
                        )
                    }
                }
        }
    }
}
