package org.athletica.crm.core

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Диапазон дат (включительно с обеих сторон).
 * Пересечение года допустимо (например, 29 дек — 5 янв — для фильтров по дню рождения).
 */
@Serializable
data class DateRange(
    /** Начало диапазона (включительно). */
    val from: LocalDate,
    /** Конец диапазона (включительно). */
    val to: LocalDate,
)
