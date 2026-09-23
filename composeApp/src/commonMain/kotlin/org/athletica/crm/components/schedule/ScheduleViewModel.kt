package org.athletica.crm.components.schedule

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.athletica.crm.components.settings.toSettingsApiError

/**
 * ViewModel страницы расписания.
 * При создании загружает справочники фильтров (один раз) и занятия недели, содержащей [today];
 * при смене недели или фильтров перезапрашивает занятия, сохраняя остальное состояние.
 */
class ScheduleViewModel(
    private val source: ScheduleSource,
    private val scope: CoroutineScope,
    today: LocalDate,
) {
    var state: ScheduleState by mutableStateOf(ScheduleState.forWeekOf(today))
        private set

    init {
        scope.launch {
            val dictionaries = source.dictionaries()
            state = state.withDictionaries(dictionaries)
        }
        loadSessions()
    }

    /** Показать предыдущую неделю. */
    fun onPreviousWeek() {
        state = state.shiftedBy(-1)
        loadSessions()
    }

    /** Показать следующую неделю. */
    fun onNextWeek() {
        state = state.shiftedBy(1)
        loadSessions()
    }

    /** Применить фильтры [filters] к отображаемой неделе. */
    fun onFiltersChange(filters: ScheduleFilters) {
        if (filters == state.filters) {
            return
        }
        state = state.withFilters(filters)
        loadSessions()
    }

    /** Повторить загрузку занятий после ошибки. */
    fun onRetry() {
        state = state.reloading()
        loadSessions()
    }

    /** Запрашивает занятия для текущих недели и фильтров. */
    private fun loadSessions() {
        val request = state.request
        scope.launch {
            val result =
                source.sessions(request).fold(
                    ifLeft = { ScheduleLoadState.Error(it.toSettingsApiError()) },
                    ifRight = { ScheduleLoadState.Loaded(it.sessions) },
                )
            state = state.withResult(request, result)
        }
    }
}
