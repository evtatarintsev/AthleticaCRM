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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.groups.GroupCreateRequest
import org.athletica.crm.api.schemas.groups.GroupListItem
import org.athletica.crm.api.schemas.groups.GroupListRequest
import kotlin.uuid.Uuid

/**
 * Экран списка групп организации с поиском, чекбоксами и меню действий.
 * Загружает данные через [api] при первом отображении.
 */
@Composable
fun GroupsScreen(
    api: ApiClient,
    modifier: Modifier = Modifier,
) {
    var groups by remember { mutableStateOf<List<GroupListItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(GroupFilterState()) }
    var selectedIds by remember { mutableStateOf<Set<Uuid>>(emptySet()) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
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

    LaunchedEffect(showCreateDialog) {
        if (!showCreateDialog) {
            newGroupName = ""
            createError = null
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { if (!isCreating) showCreateDialog = false },
            title = { Text("Новая группа") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        label = { Text("Название") },
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
                                .createGroup(GroupCreateRequest(id = Uuid.generateV7(), name = newGroupName))
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
                                        groups = groups + GroupListItem(id = created.id, name = created.name)
                                        isCreating = false
                                        showCreateDialog = false
                                    },
                                )
                        }
                    },
                    enabled = newGroupName.isNotBlank() && !isCreating,
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text("Создать")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }, enabled = !isCreating) {
                    Text("Отмена")
                }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (selectedIds.isEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { showCreateDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Добавить группу") },
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
                        // Строка поиска
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            OutlinedTextField(
                                value = filter.nameQuery,
                                onValueChange = { filter = filter.copy(nameQuery = it) },
                                placeholder = {
                                    Text(
                                        "Поиск по названию...",
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
                                                contentDescription = "Очистить поиск",
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
                                    Text("Групп пока нет", style = MaterialTheme.typography.bodyLarge)
                                }

                            filteredGroups.isEmpty() ->
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Ничего не найдено", style = MaterialTheme.typography.bodyLarge)
                                }

                            else ->
                                LazyColumn(
                                    contentPadding = PaddingValues(
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
                                                    if (checked) selectedIds + group.id
                                                    else selectedIds - group.id
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
 * [selectedCount] — количество выбранных записей.
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
            text = "Выбрано: $selectedCount",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f),
        )
        IconButton(onClick = onDelete) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = "Удалить выбранные")
        }
        IconButton(onClick = onNotify) {
            Icon(imageVector = Icons.Default.Notifications, contentDescription = "Оповестить выбранные")
        }
    }
}
