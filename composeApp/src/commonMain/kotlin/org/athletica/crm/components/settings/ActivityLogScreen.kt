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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.audit.AuditLogItem
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_back
import org.athletica.crm.generated.resources.action_next_page
import org.athletica.crm.generated.resources.action_previous_page
import org.athletica.crm.generated.resources.label_ip_address
import org.athletica.crm.generated.resources.label_pagination
import org.athletica.crm.generated.resources.screen_activity_log
import org.jetbrains.compose.resources.stringResource

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
    val scope = rememberCoroutineScope()
    val viewModel = remember { ActivityLogViewModel(api, scope) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.screen_activity_log)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
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
            when (val s = viewModel.state) {
                is ActivityLogState.Loading ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        CircularProgressIndicator()
                    }

                is ActivityLogState.Error ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Text(
                            text = s.error.message(),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                is ActivityLogState.Loaded -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(s.items) { item ->
                            AuditLogItemCard(item)
                        }
                    }

                    PaginationBar(
                        page = viewModel.page,
                        total = s.total,
                        pageSize = viewModel.pageSize,
                        onPrevious = { viewModel.prevPage() },
                        onNext = { viewModel.nextPage() },
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
                            if (item.entityId != null) {
                                append(" • ${item.entityId}")
                            }
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item.ipAddress?.let {
                Text(
                    text = stringResource(Res.string.label_ip_address, it),
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
            Icon(
                Icons.AutoMirrored.Filled.NavigateBefore,
                contentDescription = stringResource(Res.string.action_previous_page),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(Res.string.label_pagination, page + 1, totalPages),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onNext, enabled = (page + 1) * pageSize < total) {
            Icon(
                Icons.AutoMirrored.Filled.NavigateNext,
                contentDescription = stringResource(Res.string.action_next_page),
            )
        }
    }
}

/** Форматирует ISO-строку временной метки для отображения. */
private fun formatTimestamp(iso: String): String = iso.take(16).replace("T", " ")
