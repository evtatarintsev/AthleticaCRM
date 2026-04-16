package org.athletica.crm.components.clients

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.api.schemas.clients.BalanceJournalEntry
import org.athletica.crm.core.ClientId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.balance_history_empty
import org.athletica.crm.generated.resources.section_balance_history
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * Шторка истории операций по балансу клиента.
 * Загружает записи из [api] по [clientId], отображает в обратном хронологическом порядке.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalanceHistorySheet(
    api: ApiClient,
    clientId: ClientId,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var entries by remember { mutableStateOf<List<BalanceJournalEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(clientId) {
        api.clientBalanceHistory(clientId).fold(
            ifLeft = { err ->
                error =
                    when (err) {
                        is ApiClientError.Unauthenticated -> "Сессия истекла"
                        is ApiClientError.ValidationError -> err.message
                        is ApiClientError.Unavailable -> "Сервис недоступен"
                    }
                isLoading = false
            },
            ifRight = { response ->
                entries = response.entries
                isLoading = false
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(Res.string.section_balance_history),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            HorizontalDivider()

            when {
                isLoading -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    ) {
                        CircularProgressIndicator()
                    }
                }

                error != null -> {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }

                entries.isEmpty() -> {
                    Text(
                        text = stringResource(Res.string.balance_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }

                else -> {
                    LazyColumn {
                        items(entries, key = { it.id }) { entry ->
                            BalanceEntryItem(entry)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceEntryItem(entry: BalanceJournalEntry) {
    val isPositive = entry.amount >= 0
    val amountColor =
        if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val amountPrefix = if (isPositive) "+" else ""

    ListItem(
        headlineContent = {
            Text(
                text = "$amountPrefix${entry.amount.formatBalance()}",
                color = amountColor,
                fontWeight = FontWeight.Medium,
            )
        },
        supportingContent = {
            Column {
                val note = entry.note
                if (!note.isNullOrBlank()) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                val meta =
                    buildString {
                        entry.performedBy?.let { append(it.name) }
                        append(" · ")
                        append(entry.createdAt.formatDateTime())
                    }
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            Text(
                text = entry.balanceAfter.formatBalance(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

private fun Instant.formatDateTime(): String {
    val local = toLocalDateTime(TimeZone.currentSystemDefault())
    val day = local.day.toString().padStart(2, '0')
    val month = local.month.number.toString().padStart(2, '0')
    val year = local.year
    val hour = local.hour.toString().padStart(2, '0')
    val minute = local.minute.toString().padStart(2, '0')
    return "$day.$month.$year $hour:$minute"
}
