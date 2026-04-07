package org.athletica.crm.api.schemas.groups

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Дисциплина, привязанная к группе.
 * [id] — идентификатор дисциплины.
 * [name] — название дисциплины.
 */
@Serializable
data class GroupDiscipline(
    /** Идентификатор дисциплины. */
    val id: Uuid,
    /** Название дисциплины. */
    val name: String,
)
