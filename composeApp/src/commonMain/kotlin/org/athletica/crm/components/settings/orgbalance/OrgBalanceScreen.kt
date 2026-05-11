package org.athletica.crm.components.settings.orgbalance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.orgbalance.OrgBalanceJournalEntry
import org.athletica.crm.components.settings.message
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.action_back
import org.athletica.crm.generated.resources.action_replenish_balance
import org.athletica.crm.generated.resources.label_total_balance
import org.athletica.crm.generated.resources.org_balance_empty
import org.athletica.crm.generated.resources.replenish_unavailable_stub
import org.athletica.crm.generated.resources.screen_org_balance
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.round
import kotlin.time.Instant

/**
 * Экран «Баланс организации».
 * Сверху показывает текущий баланс, ниже — историю операций.
 * FAB «Пополнить баланс» открывает [ReplenishBalanceDialog]; до подключения
 * платежной интеграции при подтверждении показывается snackbar-заглушка.
 *
 * [api] — клиент API.
 * [onBack] — callback возврата на экран настроек.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrgBalanceScreen(
    api: ApiClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { OrgBalanceViewModel(api, scope) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showReplenish by remember { mutableStateOf(false) }
    val stubMessage = stringResource(Res.string.replenish_unavailable_stub)

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.screen_org_balance)) },
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showReplenish = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(Res.string.action_replenish_balance)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            when (val s = viewModel.loadState) {
                is OrgBalanceLoadState.Loading ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        CircularProgressIndicator()
                    }

                is OrgBalanceLoadState.Error ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Text(
                            text = s.error.message(),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                is OrgBalanceLoadState.Loaded ->
                    Column(modifier = Modifier.fillMaxSize()) {
                        TotalBalanceCard(amount = s.totalAmount)

                        if (s.history.isEmpty()) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Text(
                                    text = stringResource(Res.string.org_balance_empty),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(bottom = 80.dp),
                            ) {
                                items(s.history, key = { it.id.toString() }) { entry ->
                                    OrgBalanceEntryItem(entry)
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
            }
        }
    }

    if (showReplenish) {
        ReplenishBalanceDialog(
            onPay = { _ ->
                showReplenish = false
                scope.launch { snackbarHostState.showSnackbar(stubMessage) }
            },
            onDismiss = { showReplenish = false },
        )
    }
}

@Composable
private fun TotalBalanceCard(amount: Double) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = stringResource(Res.string.label_total_balance),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = amount.formatBalance(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun OrgBalanceEntryItem(entry: OrgBalanceJournalEntry) {
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
                val description = entry.description
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                val meta =
                    buildString {
                        append(entry.operationType)
                        entry.performedBy?.let {
                            append(" · ")
                            append(it.name)
                        }
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

private fun Double.formatBalance(): String {
    val sign = if (this < 0) "−" else ""
    val totalCents = round(abs(this) * 100).toLong()
    val rubles = totalCents / 100
    val cents = totalCents % 100
    val rublesStr = rubles.toString()
    val grouped =
        buildString {
            val len = rublesStr.length
            rublesStr.forEachIndexed { i, c ->
                if (i > 0 && (len - i) % 3 == 0) append(' ')
                append(c)
            }
        }
    val centsStr = cents.toString().padStart(2, '0')
    return "$sign$grouped,$centsStr ₽"
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
