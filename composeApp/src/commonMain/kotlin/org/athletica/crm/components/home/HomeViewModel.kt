package org.athletica.crm.components.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.clients.ClientListItem
import org.athletica.crm.api.schemas.clients.ClientListRequest
import org.athletica.crm.api.schemas.home.TodaySessionItem
import org.athletica.crm.components.settings.SettingsApiError
import org.athletica.crm.components.settings.toSettingsApiError

private const val DEBTORS_WIDGET_LIMIT = 10

/** Состояние загрузки занятий на сегодня. */
sealed class HomeLoadState {
    /** Загрузка в процессе. */
    data object Loading : HomeLoadState()

    /** Данные загружены. */
    data class Loaded(val sessions: List<TodaySessionItem>) : HomeLoadState()

    /** Ошибка загрузки. */
    data class Error(val error: SettingsApiError) : HomeLoadState()
}

/** Состояние загрузки должников. */
sealed class HomeDebtorsState {
    /** Загрузка в процессе. */
    data object Loading : HomeDebtorsState()

    /** Данные загружены: [items] — страница должников, [total] — общее количество. */
    data class Loaded(val items: List<ClientListItem>, val total: Int) : HomeDebtorsState()

    /** Ошибка загрузки. */
    data class Error(val error: SettingsApiError) : HomeDebtorsState()
}

/**
 * Состояние главной страницы.
 * Объединяет состояние занятий на сегодня и состояние виджета должников.
 */
data class HomeState(
    val sessions: HomeLoadState = HomeLoadState.Loading,
    val debtors: HomeDebtorsState = HomeDebtorsState.Loading,
)

/**
 * ViewModel главной страницы.
 * Параллельно загружает занятия на текущую дату и список клиентов с задолженностью.
 */
class HomeViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
) {
    var state by mutableStateOf(HomeState())
        private set

    init {
        load()
    }

    /** Перезагружает занятия на сегодня и список должников параллельно. */
    fun load() {
        scope.launch {
            state = HomeState()
            coroutineScope {
                val sessionsDeferred = async { api.home.todaySessions() }
                val debtorsDeferred =
                    async {
                        api.clients.list(ClientListRequest(hasDebt = true, limit = DEBTORS_WIDGET_LIMIT))
                    }
                val sessions =
                    sessionsDeferred.await().fold(
                        ifLeft = { HomeLoadState.Error(it.toSettingsApiError()) },
                        ifRight = { HomeLoadState.Loaded(it.sessions) },
                    )
                val debtors =
                    debtorsDeferred.await().fold(
                        ifLeft = { HomeDebtorsState.Error(it.toSettingsApiError()) },
                        ifRight = { HomeDebtorsState.Loaded(it.clients, it.total.toInt()) },
                    )
                state = HomeState(sessions = sessions, debtors = debtors)
            }
        }
    }
}
