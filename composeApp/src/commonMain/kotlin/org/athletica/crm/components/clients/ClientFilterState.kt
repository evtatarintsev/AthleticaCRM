package org.athletica.crm.components.clients

import org.athletica.crm.api.schemas.clients.ClientListItem
import org.athletica.crm.core.Gender
import kotlin.math.abs
import kotlin.math.round

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
                (gender == GenderFilter.All || client.gender == gender.value) &&
                (birthYearFrom == null || data.birthYear >= birthYearFrom) &&
                (birthYearTo == null || data.birthYear <= birthYearTo) &&
                (!hasDebtOnly || client.balance < 0) &&
                (!noGroupOnly || client.groups.isEmpty())
        }
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

// TODO: убрать после появления реального поля birthday в API.
internal data class FakeClientData(
    val birthYear: Int,
)

/** Вычисляет фейковые данные клиента детерминированно из имени. */
internal fun ClientListItem.fakeData(): FakeClientData {
    val h = abs(name.hashCode())
    return FakeClientData(
        birthYear = 1970 + h % 36,
    )
}

/**
 * Форматирует баланс для отображения.
 * Положительный: "1 200,00 ₽", отрицательный: "−1 200,00 ₽", ноль: "0,00 ₽".
 */
internal fun Double.formatBalance(): String {
    val sign = if (this < 0) "−" else ""
    val totalCents = round(abs(this) * 100).toLong()
    val rubles = totalCents / 100
    val cents = totalCents % 100
    val rublesStr = rubles.toString()
    val formatted =
        buildString {
            val len = rublesStr.length
            rublesStr.forEachIndexed { i, c ->
                if (i > 0 && (len - i) % 3 == 0) append('\u00a0')
                append(c)
            }
        }
    return "$sign$formatted,${cents.toString().padStart(2, '0')} ₽"
}
