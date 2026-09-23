package org.athletica.crm.core.sessions

import kotlinx.serialization.Serializable

/** Статус занятия. */
@Serializable
enum class SessionStatus {
    /** Занятие запланировано и ещё не проведено. */
    SCHEDULED,

    /** Занятие проведено. */
    COMPLETED,

    /** Занятие отменено. */
    CANCELLED,
}
