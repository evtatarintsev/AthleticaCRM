package org.athletica.crm.api.schemas.groups

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.GroupId

/**
 * Запрос на установку дисциплин группы.
 * Полностью заменяет текущий список дисциплин группы.
 * [groupId] — идентификатор группы.
 * [disciplineIds] — новый список идентификаторов дисциплин.
 */
@Serializable
data class SetGroupDisciplinesRequest(
    /** Идентификатор группы. */
    val groupId: GroupId,
    /** Новый список дисциплин группы. */
    val disciplineIds: List<DisciplineId>,
)
