package org.athletica.crm.api.schemas.groups

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.DisciplineId

/**
 * Дисциплина, привязанная к группе.
 * [id] — идентификатор дисциплины.
 * [name] — название дисциплины.
 */
@Serializable
data class GroupDiscipline(
    /** Идентификатор дисциплины. */
    val id: DisciplineId,
    /** Название дисциплины. */
    val name: String,
)
