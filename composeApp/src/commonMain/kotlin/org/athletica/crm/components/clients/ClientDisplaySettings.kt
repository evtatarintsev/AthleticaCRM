package org.athletica.crm.components.clients

/**
 * Настройки отображения таблицы клиентов.
 * [visibleColumns] — набор опциональных колонок, которые показываются.
 * Колонка «Имя» всегда видна и не входит в этот набор.
 */
data class ClientDisplaySettings(
    val visibleColumns: Set<ClientColumn> = ClientColumn.entries.toSet(),
)

/** Опциональные колонки таблицы клиентов (кроме «Имя», которое всегда видно). */
enum class ClientColumn(
    /** Отображаемое название в настройках. */
    val label: String,
) {
    Gender("Пол"),
    BirthYear("Год рождения"),
    Debt("Задолженность"),
}
