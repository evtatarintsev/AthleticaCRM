package org.athletica.crm.core.attendance

import kotlinx.serialization.Serializable

/** Вид участия клиента в занятии. */
@Serializable
enum class ParticipationKind {
    /** Постоянный участник: запись в группу активна на дату занятия. */
    REGULAR,

    /** Разовый участник, добавленный сотрудником на конкретное занятие. */
    ONE_TIME,
}
