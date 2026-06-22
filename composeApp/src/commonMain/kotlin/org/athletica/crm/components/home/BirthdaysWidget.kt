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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.athletica.crm.api.schemas.clients.ClientListItem
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.home_birthdays_age
import org.athletica.crm.generated.resources.home_birthdays_empty
import org.athletica.crm.generated.resources.home_birthdays_error
import org.athletica.crm.generated.resources.home_widget_show_all
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/**
 * Виджет «Дни рождения» — клиенты, у которых день рождения попадает в выбранное окно.
 * Показывает первую страницу результатов; при наличии дополнительных записей
 * отображает футер «Показать всех (N)».
 * [title] — отображаемый заголовок виджета.
 */
@Composable
fun BirthdaysWidget(
    title: String,
    state: HomeListState,
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
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )

            when (state) {
                is HomeListState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is HomeListState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(Res.string.home_birthdays_error),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                is HomeListState.Loaded -> {
                    if (state.items.isEmpty()) {
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
                            items(state.items) { client ->
                                BirthdayRow(
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
                                        Text(stringResource(Res.string.home_widget_show_all, state.total))
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
 * Строка именинника с именем-ссылкой и возрастом.
 */
@Composable
private fun BirthdayRow(
    client: ClientListItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
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
                text = stringResource(Res.string.home_birthdays_age, age),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
