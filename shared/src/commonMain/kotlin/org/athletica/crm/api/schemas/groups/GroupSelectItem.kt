package org.athletica.crm.api.schemas.groups

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.GroupId

/** Минимальное представление группы для использования в элементах выбора (селекторах). */
@Serializable
data class GroupSelectItem(
    /** Уникальный идентификатор группы. */
    val id: GroupId,
    /** Отображаемое название группы. */
    val name: String,
)
