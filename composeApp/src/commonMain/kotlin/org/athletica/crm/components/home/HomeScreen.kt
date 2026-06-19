package org.athletica.crm.components.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalTime
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.home.TodaySessionItem
import org.athletica.crm.api.schemas.settings.DashboardWidget
import org.athletica.crm.components.settings.DisplaySettingsViewModel
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.cd_dashboard_settings
import org.athletica.crm.generated.resources.home_birthdays_title
import org.athletica.crm.generated.resources.home_debtors_title
import org.athletica.crm.generated.resources.home_today_sessions_empty
import org.athletica.crm.generated.resources.home_today_sessions_error
import org.athletica.crm.generated.resources.home_today_sessions_title
import org.athletica.crm.generated.resources.label_hall
import org.athletica.crm.ui.WindowSize
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val WIDGET_HEIGHT = 360.dp

/**
 * Главная страница. Отображает виджеты согласно настройкам пользователя
 * ([DisplaySettingsViewModel]): на широком экране — в ряд, на узком — в одну колонку.
 * Кнопка-шестерёнка открывает диалог настройки виджетов.
 */
@Composable
fun HomeScreen(
    api: ApiClient,
    displaySettingsVm: DisplaySettingsViewModel,
    onClientClick: (ClientId) -> Unit,
    onShowAllClients: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { HomeViewModel(api, scope) }
    var showSettings by remember { mutableStateOf(false) }

    val settings = displaySettingsVm.displaySettings
    val dashboard = settings.dashboard
    val visible = dashboard.orderedVisible()

    val fetchKey = visible.map { it.fetchSignature() }.toSet()
    LaunchedEffect(fetchKey) { viewModel.load(visible) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val expanded = WindowSize.fromWidth(maxWidth) == WindowSize.EXPANDED

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { showSettings = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = stringResource(Res.string.cd_dashboard_settings),
                    )
                }
            }

            if (expanded) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    visible.forEach { widget ->
                        WidgetView(
                            widget = widget,
                            viewModel = viewModel,
                            onClientClick = onClientClick,
                            onShowAll = onShowAllClients,
                            modifier = Modifier.weight(1f).fillMaxSize(),
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    visible.forEach { widget ->
                        WidgetView(
                            widget = widget,
                            viewModel = viewModel,
                            onClientClick = onClientClick,
                            onShowAll = onShowAllClients,
                            modifier = Modifier.fillMaxWidth().height(WIDGET_HEIGHT),
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        DashboardSettingsDialog(
            settings = dashboard,
            onSettingsChange = { displaySettingsVm.update(settings.copy(dashboard = it)) },
            onDismiss = { showSettings = false },
        )
    }
}

/** Отрисовывает виджет нужного типа с его данными из [viewModel] и итоговым заголовком. */
@Composable
private fun WidgetView(
    widget: DashboardWidget,
    viewModel: HomeViewModel,
    onClientClick: (ClientId) -> Unit,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = widget.resolvedTitle()
    when (widget) {
        is DashboardWidget.Sessions ->
            SessionsWidget(title = title, state = viewModel.state.sessions, modifier = modifier)

        is DashboardWidget.Debtors ->
            DebtorsWidget(
                title = title,
                state = viewModel.state.lists[widget.id] ?: HomeListState.Loading,
                onClientClick = onClientClick,
                onShowAll = onShowAll,
                modifier = modifier,
            )

        is DashboardWidget.Birthdays ->
            BirthdaysWidget(
                title = title,
                state = viewModel.state.lists[widget.id] ?: HomeListState.Loading,
                onClientClick = onClientClick,
                onShowAll = onShowAll,
                modifier = modifier,
            )
    }
}

/** Итоговый заголовок виджета: пользовательский, либо локализованное имя типа по умолчанию. */
@Composable
internal fun DashboardWidget.resolvedTitle(): String = title?.takeIf { it.isNotBlank() } ?: stringResource(defaultTitleRes())

/** Ключ локализованного имени типа виджета по умолчанию. */
internal fun DashboardWidget.defaultTitleRes(): StringResource =
    when (this) {
        is DashboardWidget.Sessions -> Res.string.home_today_sessions_title
        is DashboardWidget.Debtors -> Res.string.home_debtors_title
        is DashboardWidget.Birthdays -> Res.string.home_birthdays_title
    }

/**
 * Виджет «Занятия сегодня» — заголовок, разделитель и список занятий в OutlinedCard.
 * [title] — отображаемый заголовок виджета.
 */
@Composable
private fun SessionsWidget(
    title: String,
    state: HomeLoadState,
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
