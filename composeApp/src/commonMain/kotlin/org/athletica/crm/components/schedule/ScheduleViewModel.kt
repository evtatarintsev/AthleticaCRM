package org.athletica.crm.components.schedule

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * ViewModel страницы расписания.
 * Хранит отображаемую неделю и переключает её кнопками навигации.
 * Данные пока берутся из заглушки [scheduleStubWeek].
 */
class ScheduleViewModel {
    var state: ScheduleState by
        mutableStateOf(
            ScheduleState.forWeekOf(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date),
        )
        private set

    /** Показать предыдущую неделю. */
    fun onPreviousWeek() {
        state = state.shiftedBy(-1)
    }

    /** Показать следующую неделю. */
    fun onNextWeek() {
        state = state.shiftedBy(1)
    }
}
