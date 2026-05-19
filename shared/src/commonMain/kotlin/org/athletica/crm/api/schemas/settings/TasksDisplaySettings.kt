package org.athletica.crm.api.schemas.settings

import kotlinx.serialization.Serializable

/**
 * Настройки отображения таблицы задач.
 * [columns] — упорядоченный список ключей видимых колонок.
 * [sort] — текущая сортировка.
 * [savedViews] — пользовательские сохранённые виды.
 */
@Serializable
data class TasksDisplaySettings(
    val columns: List<String> = emptyList(),
    val sort: SortStateSchema? = null,
    val savedViews: List<SavedViewSchema> = emptyList(),
)
