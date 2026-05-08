package org.athletica.crm.core.customfields

/**
 * Ошибка валидации значения кастомного поля.
 *
 * Допустимые коды: `REQUIRED`, `INVALID_TYPE`, `UNKNOWN_OPTION`, `TOO_LONG`, `INVALID_FORMAT`.
 */
data class CustomFieldError(
    /** Ключ поля, в котором обнаружена ошибка. */
    val fieldKey: String,
    /** Код ошибки. */
    val code: String,
)
