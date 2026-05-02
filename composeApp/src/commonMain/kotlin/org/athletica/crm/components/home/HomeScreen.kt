package org.athletica.crm.components.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import org.jetbrains.compose.resources.stringResource

/**
 * Главная страница с блоком «Занятия сегодня».
 */
@Composable
fun HomeScreen(
    api: ApiClient,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { HomeViewModel(api, scope) }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.home_today_sessions_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        HorizontalDivider()

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
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.sessions) { session ->
                            SessionCard(session = session)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Карточка занятия на главной странице.
 */
@Composable
private fun SessionCard(
    session: TodaySessionItem,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = session.groupName,
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "${formatTime(session.startTime)}–${formatTime(session.endTime)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${stringResource(Res.string.label_hall)}: ${session.hallName}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun formatTime(time: LocalTime): String = time.toString().substring(0, 5)
