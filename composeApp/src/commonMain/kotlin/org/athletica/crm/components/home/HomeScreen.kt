package org.athletica.crm.components.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalTime
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.home.TodaySessionItem
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.home_today_sessions_empty
import org.athletica.crm.generated.resources.home_today_sessions_error
import org.athletica.crm.generated.resources.home_today_sessions_title
import org.athletica.crm.generated.resources.label_hall
import org.athletica.crm.ui.WindowSize
import org.jetbrains.compose.resources.stringResource

/**
 * Главная страница.
 * На широком экране (≥1200 dp) отображает три виджета в ряд:
 * «Занятия сегодня», «Должники» и «Дни рождения».
 * На узком экране — только «Занятия сегодня».
 */
@Composable
fun HomeScreen(
    api: ApiClient,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { HomeViewModel(api, scope) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val windowSize = WindowSize.fromWidth(maxWidth)

        if (windowSize == WindowSize.EXPANDED) {
            Row(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SessionsWidget(viewModel = viewModel, modifier = Modifier.weight(1f))
                DebtorsWidget(modifier = Modifier.weight(1f))
                BirthdaysWidget(modifier = Modifier.weight(1f))
            }
        } else {
            SessionsWidget(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize().padding(16.dp),
            )
        }
    }
}

/**
 * Виджет «Занятия сегодня» — заголовок, разделитель и список занятий в OutlinedCard.
 */
@Composable
private fun SessionsWidget(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.home_today_sessions_title),
                style = MaterialTheme.typography.titleMedium,
            )

            when (val state = viewModel.loadState) {
                is HomeLoadState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is HomeLoadState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(Res.string.home_today_sessions_error),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                is HomeLoadState.Loaded -> {
                    if (state.sessions.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(Res.string.home_today_sessions_empty),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(state.sessions) { session ->
                                SessionRow(session = session)
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Строка занятия в виджете «Занятия сегодня».
 */
@Composable
private fun SessionRow(
    session: TodaySessionItem,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = session.groupName,
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "${formatTime(session.startTime)}–${formatTime(session.endTime)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${stringResource(Res.string.label_hall)}: ${session.hallName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatTime(time: LocalTime): String = time.toString().substring(0, 5)
