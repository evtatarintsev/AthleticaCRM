package org.athletica.crm.components.schedule

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlin.uuid.Uuid

// TODO: заменить заглушку на загрузку расписания с сервера, когда появится API.

/**
 * Занятия недели, начинающейся с [weekStart].
 * Набор одинаков для любой недели — это демонстрационные данные для вёрстки сетки.
 */
internal fun scheduleStubWeek(weekStart: LocalDate): List<ScheduleDay> =
    (0..6).map { offset ->
        ScheduleDay(
            date = weekStart.plus(offset, DateTimeUnit.DAY),
            events = stubEvents(offset),
        )
    }

/** Занятия одного дня недели, где [dayIndex] — смещение от понедельника. */
private fun stubEvents(dayIndex: Int): List<ScheduleEvent> =
    when (dayIndex) {
        0 ->
            listOf(
                gym(),
                workout("09:00", "09:20", coach = "Денис Кондаков", hall = "Офис", freeSeats = 7),
                event(
                    title = "Новое",
                    start = "11:00",
                    end = "12:00",
                    freeSeats = 20,
                    color = ScheduleEventColor.LIME,
                ),
            )

        1 ->
            listOf(
                gym(),
                fitness(freeSeats = 20),
                workout("09:00", "10:00", coach = "Денис Кондаков", hall = "Офис", freeSeats = 7),
                event(
                    title = "Плавание",
                    start = "11:00",
                    end = "13:00",
                    freeSeats = 1,
                    tags = listOf(ScheduleEventTag.RECRUITING),
                ),
            )

        2 ->
            listOf(
                gym(),
                workout(
                    "09:00",
                    "10:00",
                    coach = "Василий Васькин",
                    hall = "Офис",
                    freeSeats = 5,
                    tags = listOf(ScheduleEventTag.RECRUITING),
                ),
                workout(
                    "10:00",
                    "11:00",
                    coach = "Василий Васькин",
                    hall = "Офис",
                    freeSeats = 16,
                    tags = listOf(ScheduleEventTag.RECRUITING),
                ),
                event(
                    title = "Фехтование",
                    start = "11:00",
                    end = "12:00",
                    freeSeats = 11,
                    color = ScheduleEventColor.GREEN,
                ),
            )

        3 ->
            listOf(
                fitness(coach = "Георгий Быков", hall = "Зал единоборств", freeSeats = 11),
                workout("09:00", "10:00", coach = "Василий Васькин", hall = "Офис", freeSeats = 8),
                event(
                    title = "Бокс",
                    start = "11:00",
                    end = "12:00",
                    freeSeats = 1,
                    tags = listOf(ScheduleEventTag.RECRUITING),
                ),
            )

        4 ->
            listOf(
                fitness(coach = "Георгий Быков", hall = "Зал единоборств", freeSeats = 11),
                workout("09:00", "09:20", coach = "Василий Васькин", hall = "Офис", freeSeats = 11),
                event(
                    title = "Получасовое занятие для виртуальных групп",
                    start = "09:30",
                    end = "10:30",
                    freeSeats = 9,
                    color = ScheduleEventColor.GREY,
                ),
                workout("11:00", "12:00", freeSeats = 18),
            )

        5 ->
            listOf(
                fitness(freeSeats = 20),
                event(
                    title = "Бег на лыжах",
                    start = "09:00",
                    end = "10:00",
                    hall = "Парк",
                    freeSeats = 1,
                    color = ScheduleEventColor.CYAN,
                ),
                workout("09:00", "09:20", coach = "Лев просто Лев", hall = "Корт", freeSeats = 7),
                event(
                    title = "Йога",
                    start = "11:00",
                    end = "12:00",
                    freeSeats = 20,
                    color = ScheduleEventColor.LILAC,
                ),
            )

        else ->
            listOf(
                workout(
                    "09:00",
                    "09:20",
                    coach = "Лев просто Лев",
                    hall = "Зал бокса",
                    freeSeats = 7,
                    tags = listOf(ScheduleEventTag.ONLINE),
                ),
                event(
                    title = "Кроссфит",
                    start = "11:00",
                    end = "12:00",
                    freeSeats = 2,
                    tags = listOf(ScheduleEventTag.OPEN_AIR),
                    color = ScheduleEventColor.ORANGE,
                ),
            )
    }

/** Занятие в тренажёрном зале — открытая площадка на весь день. */
private fun gym(): ScheduleEvent =
    event(
        title = "Тренажерный зал",
        start = "07:00",
        end = "17:00",
        freeSeats = 100,
        color = ScheduleEventColor.ORANGE,
    )

/** Утренний фитнес. */
private fun fitness(
    coach: String? = null,
    hall: String? = null,
    freeSeats: Int,
): ScheduleEvent =
    event(
        title = "Фитнес",
        start = "07:00",
        end = "08:00",
        coach = coach,
        hall = hall,
        freeSeats = freeSeats,
        color = ScheduleEventColor.PURPLE,
    )

/** Зарядка — самое частое занятие в демонстрационной неделе. */
private fun workout(
    start: String,
    end: String,
    coach: String? = null,
    hall: String? = null,
    freeSeats: Int,
    tags: List<ScheduleEventTag> = emptyList(),
): ScheduleEvent =
    event(
        title = "Зарядка",
        start = start,
        end = end,
        coach = coach,
        hall = hall,
        freeSeats = freeSeats,
        tags = tags,
    )

/** Карточка занятия с временем в формате `ЧЧ:ММ`. */
private fun event(
    title: String,
    start: String,
    end: String,
    coach: String? = null,
    hall: String? = null,
    freeSeats: Int? = null,
    tags: List<ScheduleEventTag> = emptyList(),
    color: ScheduleEventColor = ScheduleEventColor.STEEL,
): ScheduleEvent =
    ScheduleEvent(
        id = Uuid.random(),
        title = title,
        startAt = LocalTime.parse(start),
        endAt = LocalTime.parse(end),
        coach = coach,
        hall = hall,
        freeSeats = freeSeats,
        tags = tags,
        color = color,
    )
