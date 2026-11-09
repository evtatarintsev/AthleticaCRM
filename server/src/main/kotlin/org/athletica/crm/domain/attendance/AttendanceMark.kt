package org.athletica.crm.domain.attendance

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.attendance.AttendancePresence
import org.athletica.crm.core.entityids.AttendanceLabelId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError

/**
 * Отметка посещения участника занятия: не отмечен, пришёл или не пришёл.
 * «Не пришёл без метки» непредставимо: [Absent] создаётся только через [Absent.from]
 * с непустым набором меток.
 */
sealed interface AttendanceMark {
    /** Присутствие; `null` — участник не отмечен. */
    val presence: AttendancePresence?

    /** Метки, уточняющие отметку. */
    val labelIds: Set<AttendanceLabelId>

    /** Участник не отмечен. */
    data object Unmarked : AttendanceMark {
        override val presence: AttendancePresence? = null
        override val labelIds: Set<AttendanceLabelId> = emptySet()
    }

    /** Участник пришёл; метки необязательны. */
    data class Present(
        override val labelIds: Set<AttendanceLabelId> = emptySet(),
    ) : AttendanceMark {
        override val presence: AttendancePresence = AttendancePresence.PRESENT
    }

    /** Участник не пришёл; набор меток непуст. */
    @ConsistentCopyVisibility
    data class Absent private constructor(
        override val labelIds: Set<AttendanceLabelId>,
    ) : AttendanceMark {
        override val presence: AttendancePresence = AttendancePresence.ABSENT

        companion object {
            /** Отсутствие с метками [labelIds]; для пустого набора поднимает ошибку `ATTENDANCE_ABSENCE_REQUIRES_LABEL`. */
            context(raise: Raise<AppError>)
            fun from(labelIds: Set<AttendanceLabelId>): Absent {
                if (labelIds.isEmpty()) {
                    raise(CommonDomainError("ATTENDANCE_ABSENCE_REQUIRES_LABEL", ABSENCE_REQUIRES_LABEL_MESSAGE))
                }
                return Absent(labelIds)
            }
        }
    }

    companion object {
        private const val ABSENCE_REQUIRES_LABEL_MESSAGE = "Для отметки «не пришёл» укажите хотя бы одну метку"
        private const val LABELS_WITHOUT_PRESENCE_MESSAGE = "Метки можно назначить только отмеченному участнику"

        /**
         * Отметка из присутствия [presence] и меток [labelIds].
         * Поднимает ошибку `ATTENDANCE_ABSENCE_REQUIRES_LABEL` для отсутствия без меток и
         * `ATTENDANCE_LABELS_WITHOUT_PRESENCE` для меток у неотмеченного участника.
         */
        context(raise: Raise<AppError>)
        fun from(presence: AttendancePresence?, labelIds: Collection<AttendanceLabelId>): AttendanceMark =
            when (presence) {
                null -> {
                    if (labelIds.isNotEmpty()) {
                        raise(CommonDomainError("ATTENDANCE_LABELS_WITHOUT_PRESENCE", LABELS_WITHOUT_PRESENCE_MESSAGE))
                    }
                    Unmarked
                }

                AttendancePresence.PRESENT -> {
                    Present(labelIds.toSet())
                }

                AttendancePresence.ABSENT -> {
                    Absent.from(labelIds.toSet())
                }
            }
    }
}
