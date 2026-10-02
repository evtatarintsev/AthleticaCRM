package org.athletica.crm.core.attendance

import kotlinx.serialization.Serializable

/** Применимость метки посещаемости к состоянию отметки. */
@Serializable
enum class AttendanceLabelScope {
    /** Метка уточняет присутствие (опоздал, ушёл раньше). */
    PRESENT,

    /** Метка уточняет отсутствие (болеет, прогул). */
    ABSENT,

    /** Метка применима к обоим состояниям. */
    ANY,
    ;

    /** Применима ли метка к присутствию [presence]. */
    fun appliesTo(presence: AttendancePresence): Boolean =
        when (this) {
            ANY -> true
            PRESENT -> presence == AttendancePresence.PRESENT
            ABSENT -> presence == AttendancePresence.ABSENT
        }
}
