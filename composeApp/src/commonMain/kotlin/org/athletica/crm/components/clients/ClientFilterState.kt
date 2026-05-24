package org.athletica.crm.components.clients

import org.athletica.crm.core.Gender

/**
 * Состояние фильтров списка клиентов.
 * Все поля имеют значения по умолчанию, соответствующие «без фильтра».
 */
data class ClientFilterState(
    val gender: GenderFilter = GenderFilter.All,
    val hasDebtOnly: Boolean = false,
    val noGroupOnly: Boolean = false,
) {
    /** Количество активных фильтров для бейджа кнопки «Фильтры». */
    val activeCount: Int
        get() =
            listOf(
                gender != GenderFilter.All,
                hasDebtOnly,
                noGroupOnly,
            ).count { it }
}

/** Фильтр по полу клиента. */
enum class GenderFilter(
    /** Отображаемое название. */
    val label: String,
    /** Соответствующее значение [Gender], либо null для «Все». */
    val value: Gender?,
) {
    All("Все", null),
    Male("Мужской", Gender.MALE),
    Female("Женский", Gender.FEMALE),
}
