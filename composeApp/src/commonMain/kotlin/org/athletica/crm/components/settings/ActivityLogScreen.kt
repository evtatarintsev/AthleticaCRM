package org.athletica.crm.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.audit.AuditLogItem

private const val PAGE_SIZE = 50

/**
 * Экран «Лог действий пользователей».
 * Загружает историю действий через [api] с пагинацией.
 *
 * [api] — клиент API.
 * [onBack] — callback возврата на экран настроек.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogScreen(
    api: ApiClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var items by remember { mutableStateOf<List<AuditLogItem>>(emptyList()) }
    var total by remember { mutableStateOf(0L) }
    var page by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(page) {
        isLoading = true
        error = null
        api.auditLogList(page = page, pageSize = PAGE_SIZE).fold(
            ifLeft = { err ->
                error =
                    when (err) {
                        is ApiClientError.ValidationError -> err.message
                        is ApiClientError.Unavailable -> "Сервис недоступен"
                        ApiClientError.Unauthenticated -> "Необходима авторизация"
                    }
            },
            ifRight = { response ->
                items = response.items
                total = response.total
            },
        )
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Лог действий") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            when {
                isLoading ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        CircularProgressIndicator()
                    }

                error != null ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Text(
                            text = error!!,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(items) { item ->
                            AuditLogItemCard(item)
                        }
                    }

                    PaginationBar(
                        page = page,
                        total = total,
                        pageSize = PAGE_SIZE,
                        onPrevious = { if (page > 0) page-- },
                        onNext = { if ((page + 1) * PAGE_SIZE < total) page++ },
                    )
                }
            }
        }
    }
}

@Composable
private fun AuditLogItemCard(item: AuditLogItem) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = item.actionType.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = formatTimestamp(item.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.username,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (item.entityType != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text =
                        buildString {
                            append(item.entityType)
                            if (item.entityId != null) append(" • ${item.entityId}")
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.ipAddress != null) {
                Text(
                    text = "IP: ${item.ipAddress}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PaginationBar(
    page: Int,
    total: Long,
    pageSize: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val totalPages = ((total - 1) / pageSize + 1).coerceAtLeast(1)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
    ) {
        IconButton(onClick = onPrevious, enabled = page > 0) {
            Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Предыдущая")
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${page + 1} / $totalPages",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onNext, enabled = (page + 1) * pageSize < total) {
            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Следующая")
        }
    }
}

/** Форматирует ISO-строку временной метки для отображения. */
private fun formatTimestamp(iso: String): String = iso.take(16).replace("T", " ")
