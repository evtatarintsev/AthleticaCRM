package org.athletica.crm.core.customfields

import kotlinx.datetime.LocalDate

/** Типизированное значение кастомного поля. */
sealed class CustomFieldValue {
    /** Строковое значение. */
    data class TextValue(val value: String) : CustomFieldValue()

    /** Числовое значение. */
    data class NumberValue(val value: Double) : CustomFieldValue()

    /** Дата. */
    data class DateValue(val value: LocalDate) : CustomFieldValue()

    /** Булево значение. */
    data class BooleanValue(val value: Boolean) : CustomFieldValue()

    /** Выбранный вариант из списка. */
    data class SelectValue(val value: String) : CustomFieldValue()

    /** Поле присутствует в определении, но значение не задано. */
    data object Empty : CustomFieldValue()
}
