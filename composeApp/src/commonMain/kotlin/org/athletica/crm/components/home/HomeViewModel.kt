package org.athletica.crm.components.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.home.TodaySessionItem
import org.athletica.crm.components.settings.SettingsApiError
import org.athletica.crm.components.settings.toSettingsApiError

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
 * ViewModel главной страницы.
 * Загружает занятия на текущую дату.
 */
class HomeViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
) {
    var loadState by mutableStateOf<HomeLoadState>(HomeLoadState.Loading)
        private set

    init {
        load()
    }

    /** Загружает занятия на сегодня. */
    fun load() {
        scope.launch {
            loadState = HomeLoadState.Loading
            api.home.todaySessions().fold(
                ifLeft = { loadState = HomeLoadState.Error(it.toSettingsApiError()) },
                ifRight = { response ->
                    loadState = HomeLoadState.Loaded(response.sessions)
                },
            )
        }
    }
}
