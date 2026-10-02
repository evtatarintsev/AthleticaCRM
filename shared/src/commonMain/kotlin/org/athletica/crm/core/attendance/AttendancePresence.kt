package org.athletica.crm.core.attendance

import kotlinx.serialization.Serializable

/** Присутствие участника на занятии; отсутствие отметки выражается отсутствием значения. */
@Serializable
enum class AttendancePresence {
    /** Участник пришёл. */
    PRESENT,

    /** Участник не пришёл. */
    ABSENT,
}
