package org.athletica.crm.read.schedule

import org.athletica.crm.api.schemas.schedule.SessionColorKey
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.GroupId

/**
 * Запасной ключ палитры карточки занятия, когда ни у группы, ни у дисциплины нет цвета.
 * Выводится из младших битов UUID первой дисциплины [firstDisciplineId], а при её
 * отсутствии — группы [groupId], по модулю размера палитры. Биты UUID, а не `hashCode()`,
 * дают один и тот же ключ в любом рантайме и версии.
 */
fun colorKeyFor(
    groupId: GroupId,
    firstDisciplineId: DisciplineId?,
): SessionColorKey {
    val source = firstDisciplineId?.value ?: groupId.value
    val leastSignificantBits = source.toLongs { _, lsb -> lsb }
    val palette = SessionColorKey.palette
    return palette[leastSignificantBits.mod(palette.size)]
}
