package org.athletica.crm.domain.attendance

import org.athletica.crm.core.attendance.ParticipationKind
import org.athletica.crm.core.entityids.ClientId

/** Участник занятия: клиент, вид его участия и отметка. */
data class JournalParticipant(
    /** Клиент-участник. */
    val clientId: ClientId,
    /** Постоянный участник группы или разовый. */
    val kind: ParticipationKind,
    /** Текущая отметка. */
    val mark: AttendanceMark,
)
