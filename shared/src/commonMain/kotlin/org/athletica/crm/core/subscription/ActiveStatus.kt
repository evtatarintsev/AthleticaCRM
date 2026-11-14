package org.athletica.crm.core.subscription

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/** Остаток занятий активного абонемента. */
sealed interface Remaining {
    /** Есть [sessions] занятий. */
    data class Left(
        /** Количество оставшихся занятий, больше нуля. */
        val sessions: Int,
    ) : Remaining

    /** Занятия закончились (остаток 0). */
    data object Exhausted : Remaining

    /** Безлимит. */
    data object Unlimited : Remaining
}

/**
 * Выводимые на дату свойства активного абонемента; не хранятся.
 * «К закрытию» ([closePending]) — срок вышел или остаток исчерпан.
 */
data class ActiveStatus(
    /** Положение даты относительно срока. */
    val termPosition: TermPosition,
    /** Остаток занятий. */
    val remaining: Remaining,
    /** Причины признака «заканчивается»; пусто — не заканчивается. */
    val endingSoon: Set<EndingSoonReason>,
) {
    /** Абонемент «к закрытию»: срок вышел или занятия закончились. */
    val closePending: Boolean
        get() = termPosition == TermPosition.EXPIRED || remaining == Remaining.Exhausted

    /**
     * Нужно ли уведомление «к закрытию» при режиме закрытия [closeMode]: абонемент «к закрытию»,
     * а режим его сам не закроет. «Вручную» — при любом «к закрытию»; «при списании последнего» —
     * при вышедшем сроке и оставшихся занятиях; «по истечении срока» — никогда.
     */
    fun needsCloseNotification(closeMode: CloseMode): Boolean =
        when (closeMode) {
            CloseMode.MANUAL -> closePending
            CloseMode.ON_LAST_SESSION -> termPosition == TermPosition.EXPIRED && remaining is Remaining.Left
            CloseMode.ON_EXPIRY -> false
        }

    companion object {
        /** Порог «заканчивается по дате»: до даты окончания столько дней или меньше. */
        const val ENDING_SOON_DAYS = 7

        /** Порог «заканчивается по занятиям»: остаток ровно столько занятий. */
        const val ENDING_SOON_SESSIONS = 1

        /**
         * Свойства абонемента со сроком [startDate]..[endDate] (включительно) и остатком
         * [sessionsRemaining] (`null` — безлимит) на дату [date].
         */
        fun of(
            startDate: LocalDate,
            endDate: LocalDate,
            sessionsRemaining: Int?,
            date: LocalDate,
        ): ActiveStatus {
            val termPosition =
                when {
                    date < startDate -> TermPosition.NOT_STARTED
                    date > endDate -> TermPosition.EXPIRED
                    else -> TermPosition.IN_TERM
                }
            val remaining =
                when {
                    sessionsRemaining == null -> Remaining.Unlimited
                    sessionsRemaining <= 0 -> Remaining.Exhausted
                    else -> Remaining.Left(sessionsRemaining)
                }
            val endingSoon =
                if (termPosition == TermPosition.IN_TERM) {
                    buildSet {
                        if (date.daysUntil(endDate) <= ENDING_SOON_DAYS) {
                            add(EndingSoonReason.BY_DATE)
                        }
                        if (remaining == Remaining.Left(ENDING_SOON_SESSIONS)) {
                            add(EndingSoonReason.BY_SESSIONS)
                        }
                    }
                } else {
                    emptySet()
                }
            return ActiveStatus(termPosition, remaining, endingSoon)
        }
    }
}
