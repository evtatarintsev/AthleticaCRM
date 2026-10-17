package org.athletica.crm.domain.attendancecontrol

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Окно закрытия журнала: сколько минут после окончания занятия по расписанию даётся на закрытие
 * журнала, прежде чем появится задача «Закрыть журнал». Допустимы значения
 * от [MIN_MINUTES] до [MAX_MINUTES] включительно; проверяются при создании.
 */
@JvmInline
value class JournalCloseDelay private constructor(
    /** Длительность окна в минутах. */
    val minutes: Int,
) {
    /** Длительность окна. */
    val duration: Duration get() = minutes.minutes

    companion object {
        /** Минимальное окно — 15 минут. */
        const val MIN_MINUTES = 15

        /** Максимальное окно — неделя. */
        const val MAX_MINUTES = 7 * 24 * 60

        /** Окно по умолчанию — 2 часа. */
        val DEFAULT = JournalCloseDelay(120)

        /** Окно длиной [minutes] минут или ошибка `INVALID_JOURNAL_CLOSE_DELAY` вне допустимого диапазона. */
        fun from(minutes: Int): Either<DomainError, JournalCloseDelay> =
            if (minutes in MIN_MINUTES..MAX_MINUTES) {
                JournalCloseDelay(minutes).right()
            } else {
                CommonDomainError(
                    "INVALID_JOURNAL_CLOSE_DELAY",
                    "Время на закрытие журнала должно быть от $MIN_MINUTES минут до $MAX_MINUTES минут",
                ).left()
            }
    }
}
