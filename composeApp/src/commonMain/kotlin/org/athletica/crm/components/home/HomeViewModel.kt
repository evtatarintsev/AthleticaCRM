package org.athletica.crm.components.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.clients.ClientListItem
import org.athletica.crm.api.schemas.clients.ClientListRequest
import org.athletica.crm.api.schemas.home.TodaySessionItem
import org.athletica.crm.api.schemas.settings.BirthdayWindow
import org.athletica.crm.api.schemas.settings.DashboardWidget
import org.athletica.crm.components.settings.SettingsApiError
import org.athletica.crm.components.settings.toSettingsApiError
import org.athletica.crm.core.DateRange
import org.athletica.crm.core.entityids.DashboardWidgetId
import kotlin.time.Clock

/** Состояние загрузки занятий на сегодня. */
sealed class HomeLoadState {
    /** Загрузка в процессе. */
    data object Loading : HomeLoadState()

    /** Данные загружены. */
    data class Loaded(val sessions: List<TodaySessionItem>) : HomeLoadState()

    /** Ошибка загрузки. */
    data class Error(val error: SettingsApiError) : HomeLoadState()
}

/**
 * Состояние загрузки списка клиентов виджета (должники, именинники и т.п.).
 * Унифицировано, поскольку оба виджета отображают страницу [ClientListItem] с общим количеством.
 */
sealed class HomeListState {
    /** Загрузка в процессе. */
    data object Loading : HomeListState()

    /** Данные загружены: [items] — страница записей, [total] — общее количество. */
    data class Loaded(val items: List<ClientListItem>, val total: Int) : HomeListState()

    /** Ошибка загрузки. */
    data class Error(val error: SettingsApiError) : HomeListState()
}

/**
 * Состояние главной страницы.
 * [sessions] — занятия на сегодня (общие для всех виджетов «Занятия»).
 * [lists] — состояние списочных виджетов (должники, именинники) по идентификатору экземпляра.
 */
data class HomeState(
    val sessions: HomeLoadState = HomeLoadState.Loading,
    val lists: Map<DashboardWidgetId, HomeListState> = emptyMap(),
)

/**
 * ViewModel главной страницы. Грузит данные по набору видимых виджетов: занятия — один раз,
 * списочные виджеты (должники/именинники) — по каждому экземпляру в соответствии с его настройками.
 */
class HomeViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
) {
    var state by mutableStateOf(HomeState())
        private set

    /** Перезагружает данные для переданного набора видимых [widgets]. */
    fun load(widgets: List<DashboardWidget>) {
        val listWidgets = widgets.filter { it is DashboardWidget.Debtors || it is DashboardWidget.Birthdays }
        val needSessions = widgets.any { it is DashboardWidget.Sessions }
        state =
            HomeState(
                sessions = if (needSessions) HomeLoadState.Loading else HomeLoadState.Loaded(emptyList()),
                lists = listWidgets.associate { it.id to HomeListState.Loading },
            )
        scope.launch {
            coroutineScope {
                val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                val sessionsDeferred = if (needSessions) async { api.home.todaySessions() } else null
                val listDeferreds =
                    listWidgets.associate { widget ->
                        widget.id to async { api.clients.list(widget.clientListRequest(today)!!) }
                    }
                val sessions =
                    sessionsDeferred?.await()?.fold(
                        ifLeft = { HomeLoadState.Error(it.toSettingsApiError()) },
                        ifRight = { HomeLoadState.Loaded(it.sessions) },
                    ) ?: HomeLoadState.Loaded(emptyList())
                val lists =
                    listDeferreds.mapValues { (_, deferred) ->
                        deferred.await().fold(
                            ifLeft = { HomeListState.Error(it.toSettingsApiError()) },
                            ifRight = { HomeListState.Loaded(it.clients, it.total.toInt()) },
                        )
                    }
                state = HomeState(sessions = sessions, lists = lists)
            }
        }
    }
}

/**
 * Запрос списка клиентов для списочного виджета, либо `null` если виджет не списочный.
 * [today] используется для вычисления окна дней рождения.
 */
internal fun DashboardWidget.clientListRequest(today: LocalDate): ClientListRequest? =
    when (this) {
        is DashboardWidget.Debtors -> ClientListRequest(hasDebt = true, limit = limit)
        is DashboardWidget.Birthdays -> ClientListRequest(birthday = window.dateRange(today), limit = limit)
        is DashboardWidget.Sessions -> null
    }

/** Диапазон дат окна дней рождения относительно [today]. */
internal fun BirthdayWindow.dateRange(today: LocalDate): DateRange =
    when (this) {
        BirthdayWindow.TODAY -> DateRange(from = today, to = today)
        BirthdayWindow.TOMORROW -> today.plus(1, DateTimeUnit.DAY).let { DateRange(from = it, to = it) }
        BirthdayWindow.WEEK -> DateRange(from = today, to = today.plus(7, DateTimeUnit.DAY))
    }

/**
 * Сигнатура виджета, определяющая необходимость перезагрузки данных. Не включает заголовок
 * и позицию: перестановка и переименование данные не меняют, изменение настроек — меняет.
 */
internal fun DashboardWidget.fetchSignature(): Any =
    when (this) {
        is DashboardWidget.Sessions -> "sessions:$id"
        is DashboardWidget.Debtors -> listOf("debtors", id, limit)
        is DashboardWidget.Birthdays -> listOf("birthdays", id, window, limit)
    }
