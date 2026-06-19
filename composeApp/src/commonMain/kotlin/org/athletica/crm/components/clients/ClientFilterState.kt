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
    /** Показывать архивных клиентов вместо активных. Это режим вида, а не фильтр-бейдж. */
    val showArchived: Boolean = false,
    /** Фильтр по дню рождения. */
    val birthdayFilter: BirthdayFilter = BirthdayFilter.None,
) {
    /** Количество активных фильтров для бейджа кнопки «Фильтры». */
    val activeCount: Int
        get() =
            listOf(
                gender != GenderFilter.All,
                hasDebtOnly,
                noGroupOnly,
                birthdayFilter != BirthdayFilter.None,
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

/** Быстрый фильтр по дню рождения клиента. */
enum class BirthdayFilter {
    /** Без фильтра. */
    None,

    /** Только клиенты с ДР сегодня. */
    Today,

    /** Только клиенты с ДР завтра. */
    Tomorrow,

    /** Только клиенты с ДР в ближайшие 7 дней. */
    ThisWeek,
}
