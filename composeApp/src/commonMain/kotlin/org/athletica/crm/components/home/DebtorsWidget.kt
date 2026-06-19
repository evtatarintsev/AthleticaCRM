package org.athletica.crm.components.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.athletica.crm.api.schemas.clients.ClientListItem
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.money.formatted
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.home_debtors_empty
import org.athletica.crm.generated.resources.home_debtors_error
import org.athletica.crm.generated.resources.home_debtors_title
import org.jetbrains.compose.resources.stringResource

/**
 * Виджет «Должники» — клиенты с отрицательным балансом.
 * Показывает первую страницу результатов; при наличии дополнительных записей
 * отображает футер «Показать всех (N)».
 */
@Composable
fun DebtorsWidget(
    state: HomeDebtorsState,
    onClientClick: (ClientId) -> Unit,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.home_debtors_title),
                style = MaterialTheme.typography.titleMedium,
            )

            when (state) {
                is HomeDebtorsState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is HomeDebtorsState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(Res.string.home_debtors_error),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                is HomeDebtorsState.Loaded -> {
                    if (state.items.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(Res.string.home_debtors_empty),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(state.items) { client ->
                                DebtorRow(
                                    client = client,
                                    onClick = { onClientClick(client.id) },
                                )
                                HorizontalDivider()
                            }
                            if (state.total > state.items.size) {
                                item {
                                    TextButton(
                                        onClick = onShowAll,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Показать всех (${state.total})")
                                    }
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
 * Строка должника с именем-ссылкой и суммой задолженности.
 */
@Composable
private fun DebtorRow(
    client: ClientListItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = client.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = client.balance.formatted,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
