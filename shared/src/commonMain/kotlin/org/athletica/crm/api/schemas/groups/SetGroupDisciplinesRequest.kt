package org.athletica.crm.api.schemas.groups

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Запрос на установку дисциплин группы.
 * Полностью заменяет текущий список дисциплин группы.
 * [groupId] — идентификатор группы.
 * [disciplineIds] — новый список идентификаторов дисциплин.
 */
@Serializable
data class SetGroupDisciplinesRequest(
    /** Идентификатор группы. */
    val groupId: Uuid,
    /** Новый список дисциплин группы. */
    val disciplineIds: List<Uuid>,
)
