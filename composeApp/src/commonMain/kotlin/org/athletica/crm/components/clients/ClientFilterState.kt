package org.athletica.crm.components.clients

import org.athletica.crm.api.schemas.clients.ClientListItem
import kotlin.math.abs

/**
 * Состояние фильтров и поиска списка клиентов.
 * Все поля имеют значения по умолчанию, соответствующие «без фильтра».
 */
data class ClientFilterState(
    val nameQuery: String = "",
    val gender: GenderFilter = GenderFilter.All,
    val birthYearFrom: Int? = null,
    val birthYearTo: Int? = null,
    val hasDebtOnly: Boolean = false,
    val noGroupOnly: Boolean = false,
) {
    /**
     * Количество активных фильтров, отображаемых чипами.
     * Поиск по имени не считается — он виден прямо в поле.
     */
    val chipCount: Int
        get() =
            listOf(
                gender != GenderFilter.All,
                birthYearFrom != null || birthYearTo != null,
                hasDebtOnly,
                noGroupOnly,
            ).count { it }

    /** Есть ли хоть один активный фильтр (включая поиск по имени). */
    val isActive: Boolean get() = nameQuery.isNotBlank() || chipCount > 0

    /** Применить к списку: возвращает отфильтрованный список клиентов. */
    fun applyTo(clients: List<ClientListItem>): List<ClientListItem> =
        clients.filter { client ->
            val data = client.fakeData()
            (nameQuery.isBlank() || client.name.contains(nameQuery, ignoreCase = true)) &&
                (gender == GenderFilter.All || data.gender == gender.code) &&
                (birthYearFrom == null || data.birthYear >= birthYearFrom) &&
                (birthYearTo == null || data.birthYear <= birthYearTo) &&
                (!hasDebtOnly || data.hasDebt) &&
                (!noGroupOnly || data.noGroup)
        }
}

/** Фильтр по полу клиента. */
enum class GenderFilter(
    /** Отображаемое название. */
    val label: String,
    /** Код, совпадающий с полем [FakeClientData.gender]. */
    val code: String,
) {
    All("Все", ""),
    Male("Мужской", "М"),
    Female("Женский", "Ж"),
}

// TODO: убрать после появления реальных полей в API.
internal data class FakeClientData(
    val gender: String,
    val birthYear: Int,
    val debtLabel: String,
    val hasDebt: Boolean,
    val noGroup: Boolean,
)

/** Вычисляет фейковые данные клиента детерминированно из имени. */
internal fun ClientListItem.fakeData(): FakeClientData {
    val h = abs(name.hashCode())
    return FakeClientData(
        gender = if (h % 2 == 0) "М" else "Ж",
        birthYear = 1970 + h % 36,
        debtLabel = when (h % 4) { 0 -> "1 200 ₽"; 1 -> "500 ₽"; else -> "—" },
        hasDebt = h % 4 < 2,
        noGroup = h % 3 == 0,
    )
}
