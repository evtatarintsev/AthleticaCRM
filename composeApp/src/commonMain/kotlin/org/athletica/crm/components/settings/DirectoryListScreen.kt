package org.athletica.crm.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.uuid.Uuid

/** Запись справочника: уникальный [id], отображаемое [name] и необязательный [photoUrl]. */
data class DirectoryItem(
    val id: Uuid,
    val name: String,
    val photoUrl: String? = null,
)

/**
 * Универсальный экран справочника (источники клиентов, виды спорта, разряды и т.д.).
 *
 * Управляет собственным состоянием: список [items], поиск, массовый выбор.
 * [title] — заголовок экрана.
 * [onBack] — переход назад.
 * [onAdd] — переход к экрану создания записи.
 * [onDeleteSelected] — удаление выбранных записей.
 * [onItemClick] — переход к редактированию записи; если `null`, записи не кликабельны.
 * [isLoading] — показывает индикатор загрузки вместо содержимого при первом запросе.
 * [error] — сообщение об ошибке загрузки; отображается, если список пуст.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryListScreen(
    title: String,
    items: List<DirectoryItem>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onDeleteSelected: (Set<Uuid>) -> Unit,
    modifier: Modifier = Modifier,
    onItemClick: ((DirectoryItem) -> Unit)? = null,
    isLoading: Boolean = false,
    error: String? = null,
) {
    var query by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf<Set<Uuid>>(emptySet()) }

    val filtered =
        if (query.isBlank()) {
            items
        } else {
            items.filter { it.name.contains(query.trim(), ignoreCase = true) }
        }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        floatingActionButton = {
            if (selectedIds.isEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Добавить") },
                )
            }
        },
        bottomBar = {
            if (selectedIds.isNotEmpty()) {
                BottomAppBar {
                    Text(
                        text = "Выбрано: ${selectedIds.size}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp).weight(1f),
                    )
                    IconButton(
                        onClick = {
                            onDeleteSelected(selectedIds)
                            selectedIds = emptySet()
                        },
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить выбранные")
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            when {
                isLoading && items.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                error != null && items.isEmpty() -> {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                    )
                }

                items.isEmpty() -> {
                    Text(
                        text = "Список пуст",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                else -> {
                    LazyColumn(
                        contentPadding =
                            PaddingValues(
                                top = 4.dp,
                                bottom = if (selectedIds.isNotEmpty()) 80.dp else 4.dp,
                            ),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        // Строка поиска
                        item(key = "search") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                OutlinedTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    placeholder = {
                                        Text(
                                            "Поиск...",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    },
                                    trailingIcon = {
                                        if (query.isNotBlank()) {
                                            IconButton(onClick = { query = "" }) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Очистить",
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
                        }

                        if (filtered.isEmpty()) {
                            item(key = "empty") {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "Ничего не найдено",
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                            }
                        } else {
                            items(filtered, key = { it.id }) { item ->
                                DirectoryItemRow(
                                    item = item,
                                    selected = item.id in selectedIds,
                                    onCheckedChange = { checked ->
                                        selectedIds =
                                            if (checked) {
                                                selectedIds + item.id
                                            } else {
                                                selectedIds - item.id
                                            }
                                    },
                                    onClick = onItemClick?.let { { it(item) } },
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

@Composable
private fun DirectoryItemRow(
    item: DirectoryItem,
    selected: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = {
            Text(item.name, fontWeight = FontWeight.Medium)
        },
        leadingContent = {
            DirectoryItemAvatar(item = item)
        },
        trailingContent = {
            Checkbox(
                checked = selected,
                onCheckedChange = onCheckedChange,
            )
        },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@Composable
fun DirectoryItemAvatar(
    item: DirectoryItem,
    modifier: Modifier = Modifier,
    size: Int = 40,
) {
    val initial = item.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
