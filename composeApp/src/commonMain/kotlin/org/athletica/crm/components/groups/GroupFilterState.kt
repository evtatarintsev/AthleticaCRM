package org.athletica.crm.components.groups

import org.athletica.crm.api.schemas.groups.GroupListItem

/**
 * Состояние поиска списка групп.
 * [nameQuery] — строка фильтрации по названию группы.
 */
data class GroupFilterState(
    val nameQuery: String = "",
) {
    /** Есть ли активный поиск. */
    val isActive: Boolean get() = nameQuery.isNotBlank()

    /** Применить к списку: возвращает отфильтрованный список групп. */
    fun applyTo(groups: List<GroupListItem>): List<GroupListItem> =
        if (nameQuery.isBlank()) {
            groups
        } else {
            groups.filter { it.name.contains(nameQuery, ignoreCase = true) }
        }
}
