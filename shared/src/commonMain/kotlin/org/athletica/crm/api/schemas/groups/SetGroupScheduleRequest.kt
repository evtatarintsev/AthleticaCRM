package org.athletica.crm.api.schemas.groups

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.GroupId

/**
 * Запрос на установку расписания группы начиная с даты [effectiveFrom].
 *
 * Установка расписания с даты заменяет всё, что действует начиная с неё, включая
 * ранее запланированные изменения. Дата в прошлом отклоняется; `null` означает сегодня.
 * Пустой [slots] означает, что с этой даты у группы нет расписания.
 */
@Serializable
data class SetGroupScheduleRequest(
    /** Группа, расписание которой устанавливается. */
    val groupId: GroupId,
    /** Дата вступления расписания в силу; `null` — сегодня. */
    val effectiveFrom: LocalDate? = null,
    /** Полный набор слотов, действующих с [effectiveFrom]. */
    val slots: List<ScheduleSlot> = emptyList(),
)
