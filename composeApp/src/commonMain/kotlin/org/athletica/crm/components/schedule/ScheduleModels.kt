package org.athletica.crm.components.schedule

import androidx.compose.ui.graphics.Color
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.athletica.crm.api.schemas.disciplines.DisciplineDetailResponse
import org.athletica.crm.api.schemas.employees.EmployeeListItem
import org.athletica.crm.api.schemas.halls.HallDetailResponse
import org.athletica.crm.api.schemas.schedule.ScheduleListRequest
import org.athletica.crm.api.schemas.schedule.ScheduleSessionSchema
import org.athletica.crm.api.schemas.schedule.SessionColorKey
import org.athletica.crm.components.settings.SettingsApiError
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.HallId

/**
 * Пара цветов карточки занятия в теме приложения.
 * [container] — фон карточки, [badge] — фон плашки со свободными местами.
 */
data class ScheduleCardColors(
    val container: Color,
    val badge: Color,
)

/** Цвета карточки для ключа палитры; неизвестный клиенту ключ рисуется нейтральным. */
fun SessionColorKey.cardColors(): ScheduleCardColors =
    when (this) {
        SessionColorKey.ORANGE -> ScheduleCardColors(Color(0xFFFBC38A), Color(0xFFD9A268))
        SessionColorKey.PURPLE -> ScheduleCardColors(Color(0xFFE4A0EA), Color(0xFFC77FCE))
        SessionColorKey.STEEL -> ScheduleCardColors(Color(0xFFD9E4EE), Color(0xFFBAC7D4))
        SessionColorKey.CYAN -> ScheduleCardColors(Color(0xFFA9E3F8), Color(0xFF80CBE5))
        SessionColorKey.GREEN -> ScheduleCardColors(Color(0xFF8FC9A4), Color(0xFF6FAE86))
        SessionColorKey.LIME -> ScheduleCardColors(Color(0xFFD7E16B), Color(0xFFB7C24D))
        SessionColorKey.LILAC -> ScheduleCardColors(Color(0xFFDAD7F6), Color(0xFFC0BCEB))
        SessionColorKey.GREY -> ScheduleCardColors(Color(0xFFDDDDDD), Color(0xFFBEBEBE))
        SessionColorKey.UNKNOWN -> NEUTRAL_CARD_COLORS
    }

/** Нейтральные цвета карточки для ключа, которого клиент не знает. */
private val NEUTRAL_CARD_COLORS = ScheduleCardColors(Color(0xFFEEEEEE), Color(0xFFD6D6D6))

/** Продолжительность занятия в виде, опускающем нулевую часть. */
sealed class ScheduleDuration {
    /** Меньше часа: только [minutes]. */
    data class Minutes(val minutes: Int) : ScheduleDuration()

    /** Целое число часов [hours]. */
    data class Hours(val hours: Int) : ScheduleDuration()

    /** [hours] часов и [minutes] минут, обе части ненулевые. */
    data class HoursMinutes(val hours: Int, val minutes: Int) : ScheduleDuration()

    companion object {
        /** Продолжительность интервала от [start] до [end] в пределах одних суток. */
        fun between(
            start: LocalTime,
            end: LocalTime,
        ): ScheduleDuration {
            val total = (end.hour * 60 + end.minute) - (start.hour * 60 + start.minute)
            val hours = total / 60
            val minutes = total % 60
            return when {
                hours == 0 -> Minutes(minutes)
                minutes == 0 -> Hours(hours)
                else -> HoursMinutes(hours, minutes)
            }
        }
    }
}

/** Один день недельной сетки: [date] — дата колонки, [sessions] — занятия этого дня. */
data class ScheduleDay(
    val date: LocalDate,
    val sessions: List<ScheduleSessionSchema>,
)

/** Выбранные значения фильтров расписания; пустое множество — фильтр не задан. */
data class ScheduleFilters(
    /** Выбранные дисциплины. */
    val disciplineIds: Set<DisciplineId> = emptySet(),
    /** Выбранные залы. */
    val hallIds: Set<HallId> = emptySet(),
    /** Выбранные тренеры. */
    val employeeIds: Set<EmployeeId> = emptySet(),
) {
    /** `true`, если не выбрано ни одно значение ни в одном фильтре. */
    val isEmpty: Boolean
        get() = disciplineIds.isEmpty() && hallIds.isEmpty() && employeeIds.isEmpty()
}

/**
 * Справочники значений фильтров: грузятся один раз при открытии страницы
 * и не зависят от отображаемой недели. Карточки их не используют.
 */
data class ScheduleDictionaries(
    /** Залы текущего филиала. */
    val halls: List<HallDetailResponse> = emptyList(),
    /** Дисциплины организации. */
    val disciplines: List<DisciplineDetailResponse> = emptyList(),
    /** Сотрудники филиала. */
    val employees: List<EmployeeListItem> = emptyList(),
)

/** Состояние загрузки занятий отображаемой недели. */
sealed class ScheduleLoadState {
    /** Загрузка в процессе. */
    data object Loading : ScheduleLoadState()

    /** Занятия загружены. */
    data class Loaded(val sessions: List<ScheduleSessionSchema>) : ScheduleLoadState()

    /** Ошибка загрузки. */
    data class Error(val error: SettingsApiError) : ScheduleLoadState()
}

/**
 * Состояние страницы расписания в недельном режиме.
 * [weekStart] — понедельник отображаемой недели, [data] — занятия недели,
 * [filters] — выбранные фильтры, [dictionaries] — значения для панели фильтров.
 */
data class ScheduleState(
    val weekStart: LocalDate,
    val data: ScheduleLoadState = ScheduleLoadState.Loading,
    val filters: ScheduleFilters = ScheduleFilters(),
    val dictionaries: ScheduleDictionaries = ScheduleDictionaries(),
) {
    /** Воскресенье отображаемой недели. */
    val weekEnd: LocalDate
        get() = weekStart.plus(6, DateTimeUnit.DAY)

    /** Запрос занятий отображаемой недели с выбранными фильтрами. */
    val request: ScheduleListRequest
        get() =
            ScheduleListRequest(
                from = weekStart,
                to = weekEnd,
                disciplineIds = filters.disciplineIds.toList(),
                hallIds = filters.hallIds.toList(),
                employeeIds = filters.employeeIds.toList(),
            )

    /** Семь колонок сетки с занятиями, разложенными по дням в порядке времени начала. */
    val days: List<ScheduleDay>
        get() {
            val sessions = (data as? ScheduleLoadState.Loaded)?.sessions.orEmpty()
            val byDate = sessions.groupBy { it.date }
            return (0..6).map { offset ->
                val date = weekStart.plus(offset, DateTimeUnit.DAY)
                ScheduleDay(date, byDate[date].orEmpty().sortedBy { it.startTime })
            }
        }

    /** Часы, в которые начинается хотя бы одно занятие недели, по возрастанию. */
    val hours: List<Int>
        get() = days.flatMap { day -> day.sessions.map { it.startTime.hour } }.distinct().sorted()

    /** Неделя, сдвинутая на [weeks] недель (отрицательное — назад), в состоянии загрузки. */
    fun shiftedBy(weeks: Int): ScheduleState = copy(weekStart = weekStart.plus(weeks * 7, DateTimeUnit.DAY), data = ScheduleLoadState.Loading)

    /** Состояние с новыми [filters], ожидающее загрузки. */
    fun withFilters(filters: ScheduleFilters): ScheduleState = copy(filters = filters, data = ScheduleLoadState.Loading)

    /** Состояние, повторно ожидающее загрузки текущей недели. */
    fun reloading(): ScheduleState = copy(data = ScheduleLoadState.Loading)

    /**
     * Состояние с результатом [result] запроса [request]. Устаревший ответ — на неделю
     * или фильтры, которые уже сменились, — игнорируется.
     */
    fun withResult(
        request: ScheduleListRequest,
        result: ScheduleLoadState,
    ): ScheduleState = if (request == this.request) copy(data = result) else this

    /** Состояние со справочниками фильтров [dictionaries]. */
    fun withDictionaries(dictionaries: ScheduleDictionaries): ScheduleState = copy(dictionaries = dictionaries)

    companion object {
        /** Начальное состояние недели, в которую попадает [date]. */
        fun forWeekOf(date: LocalDate): ScheduleState = ScheduleState(weekStart = date.minus(date.dayOfWeek.ordinal, DateTimeUnit.DAY))
    }
}
