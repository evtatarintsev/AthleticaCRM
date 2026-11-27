package org.athletica.mobile.ui

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Формат даты `03.12.2026`. */
private val DATE =
    LocalDate.Format {
        day()
        char('.')
        monthNumber()
        char('.')
        year()
    }

/** Формат времени `09:30`. */
private val TIME =
    LocalTime.Format {
        hour()
        char(':')
        minute()
    }

/** Дата для показа: `03.12.2026`. */
val LocalDate.shown: String get() = format(DATE)

/** Время для показа: `09:30`. */
val LocalTime.shown: String get() = format(TIME)

/** Момент для показа в часовом поясе устройства: `03.12.2026 09:30`. */
val Instant.shown: String get() = toLocalDateTime(TimeZone.currentSystemDefault()).let { "${it.date.shown} ${it.time.shown}" }
