package org.athletica.crm.api.schemas.groups

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** Минимальное представление группы для использования в элементах выбора (селекторах). */
@Serializable
data class GroupSelectItem(
    /** Уникальный идентификатор группы. */
    val id: Uuid,
    /** Отображаемое название группы. */
    val name: String,
)
