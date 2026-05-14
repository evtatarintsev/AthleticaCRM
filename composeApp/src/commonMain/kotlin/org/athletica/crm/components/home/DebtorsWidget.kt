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
import org.athletica.crm.core.Gender
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.home_debtors_empty
import org.athletica.crm.generated.resources.home_debtors_title
import org.jetbrains.compose.resources.stringResource

private const val WIDGET_LIMIT = 10

private val stubDebtors: List<ClientListItem> =
    listOf(
        ClientListItem(id = ClientId.new(), name = "Иванов Сергей", gender = Gender.MALE, groups = emptyList(), balance = -1500.0),
        ClientListItem(id = ClientId.new(), name = "Петрова Анна", gender = Gender.FEMALE, groups = emptyList(), balance = -800.0),
        ClientListItem(id = ClientId.new(), name = "Козлов Дмитрий", gender = Gender.MALE, groups = emptyList(), balance = -3200.0),
        ClientListItem(id = ClientId.new(), name = "Смирнова Ольга", gender = Gender.FEMALE, groups = emptyList(), balance = -450.0),
    )

/**
 * Виджет «Должники» — клиенты с отрицательным балансом.
 * Показывает не более [WIDGET_LIMIT] записей; при превышении — футер «Показать всех».
 * Данные — заглушка для оценки интерфейса.
 */
@Composable
fun DebtorsWidget(
    onClientClick: (ClientId) -> Unit,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = stubDebtors.take(WIDGET_LIMIT)
    val overflow = stubDebtors.size - visible.size

    OutlinedCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.home_debtors_title),
                style = MaterialTheme.typography.titleMedium,
            )

            if (stubDebtors.isEmpty()) {
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
                    items(visible) { client ->
                        DebtorRow(
                            client = client,
                            onClick = { onClientClick(client.id) },
                        )
                        HorizontalDivider()
                    }
                    if (overflow > 0) {
                        item {
                            TextButton(
                                onClick = onShowAll,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Показать всех (${stubDebtors.size})")
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
            text = "${client.balance.toInt()} ₽",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
