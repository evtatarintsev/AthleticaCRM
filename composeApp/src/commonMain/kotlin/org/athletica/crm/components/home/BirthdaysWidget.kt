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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.athletica.crm.api.schemas.clients.ClientListItem
import org.athletica.crm.core.Gender
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.home_birthdays_empty
import org.athletica.crm.generated.resources.home_birthdays_title
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

private const val WIDGET_LIMIT = 10

private val today: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

private val stubBirthdays: List<ClientListItem> =
    listOf(
        ClientListItem(id = ClientId.new(), name = "Новикова Елена", gender = Gender.FEMALE, groups = emptyList(), balance = Money.zero(Currency.RUB), birthday = today.run { LocalDate(year - 28, month, day) }),
        ClientListItem(id = ClientId.new(), name = "Фёдоров Михаил", gender = Gender.MALE, groups = emptyList(), balance = Money.zero(Currency.RUB), birthday = today.run { LocalDate(year - 15, month, day) }),
        ClientListItem(id = ClientId.new(), name = "Морозова Татьяна", gender = Gender.FEMALE, groups = emptyList(), balance = Money.zero(Currency.RUB), birthday = today.run { LocalDate(year - 42, month, day) }),
    )

/**
 * Виджет «Дни рождения сегодня» — клиенты, у которых сегодня день рождения.
 * Показывает не более [WIDGET_LIMIT] записей; при превышении — футер «Показать всех».
 * Данные — заглушка для оценки интерфейса.
 */
@Composable
fun BirthdaysWidget(
    onClientClick: (ClientId) -> Unit,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = stubBirthdays.take(WIDGET_LIMIT)
    val overflow = stubBirthdays.size - visible.size

    OutlinedCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.home_birthdays_title),
                style = MaterialTheme.typography.titleMedium,
            )

            if (stubBirthdays.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(Res.string.home_birthdays_empty),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(visible) { client ->
                        BirthdayRow(
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
                                Text("Показать всех (${stubBirthdays.size})")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Строка именинника с именем-ссылкой и возрастом.
 */
@Composable
private fun BirthdayRow(
    client: ClientListItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val age = client.birthday?.let { today.year - it.year }

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
        if (age != null) {
            Text(
                text = "$age лет",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
