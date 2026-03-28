package org.athletica.crm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.clients.ClientListItem
import org.athletica.crm.api.schemas.clients.ClientListRequest
import kotlin.uuid.Uuid

/**
 * Экран списка клиентов.
 * Загружает данные через [api] при первом отображении.
 */
@Composable
fun ClientsScreen(
    api: ApiClient,
    modifier: Modifier = Modifier,
) {
    var clients by remember { mutableStateOf<List<ClientListItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedIds by remember { mutableStateOf<Set<Uuid>>(emptySet()) }

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

@Composable
private fun ClientRow(
    client: ClientListItem,
    selected: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = onCheckedChange,
        )

        Spacer(Modifier.width(12.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Text(
                text = client.name.first().uppercaseChar().toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = client.name,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
