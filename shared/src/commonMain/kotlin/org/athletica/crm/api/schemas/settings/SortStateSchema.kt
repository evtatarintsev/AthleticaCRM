package org.athletica.crm.api.schemas.settings

import kotlinx.serialization.Serializable

/** Направление сортировки списка. */
@Serializable
enum class SortDirectionSchema {
    /** По возрастанию. */
    Asc,

    /** По убыванию. */
    Desc,
}

/**
 * Схема состояния сортировки.
 * [columnId] — стабильный идентификатор колонки (например, `"name"`, `"balance"`).
 * [direction] — направление сортировки.
 */
@Serializable
data class SortStateSchema(
    val columnId: String,
    val direction: SortDirectionSchema,
)
