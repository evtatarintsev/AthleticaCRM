package org.athletica.crm.api.schemas.settings

import kotlinx.serialization.Serializable

/**
 * Настройки отображения таблицы групп.
 * [columns] — упорядоченный список ключей видимых колонок.
 * [sort] — текущая сортировка.
 * [savedViews] — пользовательские сохранённые виды.
 */
@Serializable
data class GroupsDisplaySettings(
    val columns: List<String> = emptyList(),
    val sort: SortStateSchema? = null,
    val savedViews: List<SavedViewSchema> = emptyList(),
)
