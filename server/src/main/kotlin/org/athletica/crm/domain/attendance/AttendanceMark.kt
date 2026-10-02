package org.athletica.crm.domain.attendance

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.athletica.crm.core.attendance.AttendancePresence
import org.athletica.crm.core.entityids.AttendanceLabelId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError

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
            /** Отсутствие с метками [labelIds]; ошибка `ATTENDANCE_ABSENCE_REQUIRES_LABEL` для пустого набора. */
            fun from(labelIds: Set<AttendanceLabelId>): Either<DomainError, Absent> =
                if (labelIds.isEmpty()) {
                    CommonDomainError("ATTENDANCE_ABSENCE_REQUIRES_LABEL", ABSENCE_REQUIRES_LABEL_MESSAGE).left()
                } else {
                    Absent(labelIds).right()
                }
        }
    }

    companion object {
        private const val ABSENCE_REQUIRES_LABEL_MESSAGE = "Для отметки «не пришёл» укажите хотя бы одну метку"
        private const val LABELS_WITHOUT_PRESENCE_MESSAGE = "Метки можно назначить только отмеченному участнику"

        /**
         * Отметка из присутствия [presence] и меток [labelIds].
         * Ошибка `ATTENDANCE_ABSENCE_REQUIRES_LABEL` для отсутствия без меток и
         * `ATTENDANCE_LABELS_WITHOUT_PRESENCE` для меток у неотмеченного участника.
         */
        fun from(presence: AttendancePresence?, labelIds: Collection<AttendanceLabelId>): Either<DomainError, AttendanceMark> =
            when (presence) {
                null -> {
                    if (labelIds.isEmpty()) {
                        Unmarked.right()
                    } else {
                        CommonDomainError("ATTENDANCE_LABELS_WITHOUT_PRESENCE", LABELS_WITHOUT_PRESENCE_MESSAGE).left()
                    }
                }

                AttendancePresence.PRESENT -> {
                    Present(labelIds.toSet()).right()
                }

                AttendancePresence.ABSENT -> {
                    Absent.from(labelIds.toSet())
                }
            }
    }
}
