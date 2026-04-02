package org.athletica.crm.api.schemas.groups

import kotlinx.serialization.Serializable

/**
 * День недели в расписании группы.
 * Сериализуется как строка (например, "MONDAY").
 */
@Serializable
enum class DayOfWeek(val displayName: String) {
    MONDAY("Пн"),
    TUESDAY("Вт"),
    WEDNESDAY("Ср"),
    THURSDAY("Чт"),
    FRIDAY("Пт"),
    SATURDAY("Сб"),
    SUNDAY("Вс"),
}
