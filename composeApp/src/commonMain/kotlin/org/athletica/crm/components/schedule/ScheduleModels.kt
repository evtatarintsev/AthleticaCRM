package org.athletica.crm.components.schedule

import androidx.compose.ui.graphics.Color
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.uuid.Uuid

/** Метка-тег занятия, отображаемая на карточке отдельной плашкой. */
enum class ScheduleEventTag {
    /** Идёт набор в группу. */
    RECRUITING,

    /** Занятие проводится онлайн. */
    ONLINE,

    /** Занятие проводится на открытом воздухе. */
    OPEN_AIR,
}

/**
 * Цветовая схема карточки занятия.
 * [container] — фон карточки, [badge] — фон плашки со свободными местами.
 */
enum class ScheduleEventColor(val container: Color, val badge: Color) {
    ORANGE(Color(0xFFFBC38A), Color(0xFFD9A268)),
    PURPLE(Color(0xFFE4A0EA), Color(0xFFC77FCE)),
    STEEL(Color(0xFFD9E4EE), Color(0xFFBAC7D4)),
    GREY(Color(0xFFDDDDDD), Color(0xFFBEBEBE)),
    CYAN(Color(0xFFA9E3F8), Color(0xFF80CBE5)),
    GREEN(Color(0xFF8FC9A4), Color(0xFF6FAE86)),
    LIME(Color(0xFFD7E16B), Color(0xFFB7C24D)),
    LILAC(Color(0xFFDAD7F6), Color(0xFFC0BCEB)),
}

/**
 * Занятие в сетке расписания.
 *
 * [id] — идентификатор карточки, используется как ключ списка.
 * [title] — название занятия.
 * [startAt] — время начала, определяет строку сетки.
 * [endAt] — время окончания.
 * [coach] — имя тренера; `null` — тренер не назначен.
 * [hall] — зал или площадка; `null` — не указано.
 * [freeSeats] — количество свободных мест; `null` — плашка не отображается.
 * [tags] — дополнительные метки занятия.
 * [color] — цветовая схема карточки.
 */
data class ScheduleEvent(
    val id: Uuid,
    val title: String,
    val startAt: LocalTime,
    val endAt: LocalTime,
    val coach: String? = null,
    val hall: String? = null,
    val freeSeats: Int? = null,
    val tags: List<ScheduleEventTag> = emptyList(),
    val color: ScheduleEventColor = ScheduleEventColor.STEEL,
) {
    /** Продолжительность занятия в минутах. */
    val durationMinutes: Int
        get() = (endAt.hour * 60 + endAt.minute) - (startAt.hour * 60 + startAt.minute)
}

/**
 * Один день недельной сетки: [date] — дата колонки, [events] — занятия этого дня.
 */
data class ScheduleDay(
    val date: LocalDate,
    val events: List<ScheduleEvent>,
)

/**
 * Состояние страницы расписания в недельном режиме.
 * [weekStart] — понедельник отображаемой недели, [days] — семь колонок сетки.
 */
data class ScheduleState(
    val weekStart: LocalDate,
    val days: List<ScheduleDay>,
) {
    /** Воскресенье отображаемой недели. */
    val weekEnd: LocalDate
        get() = weekStart.plus(6, DateTimeUnit.DAY)

    /** Часы, в которые начинается хотя бы одно занятие недели, по возрастанию. */
    val hours: List<Int>
        get() = days.flatMap { day -> day.events.map { it.startAt.hour } }.distinct().sorted()

    /** Состояние недели, сдвинутой на [weeks] недель вперёд (отрицательное значение — назад). */
    fun shiftedBy(weeks: Int): ScheduleState = forWeekOf(weekStart.plus(weeks * 7, DateTimeUnit.DAY))

    companion object {
        /** Состояние недели, в которую попадает [date]. */
        fun forWeekOf(date: LocalDate): ScheduleState {
            val start = date.minus(date.dayOfWeek.ordinal, DateTimeUnit.DAY)
            return ScheduleState(weekStart = start, days = scheduleStubWeek(start))
        }
    }
}
