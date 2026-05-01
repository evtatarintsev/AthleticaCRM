package org.athletica.crm.core.customfields

/** Тип кастомного поля определяет допустимые значения и правила валидации. */
sealed class CustomFieldType {
    /** Строковое значение произвольной длины. */
    data object Text : CustomFieldType()

    /** Числовое значение (Double). */
    data object Number : CustomFieldType()

    /** Дата (LocalDate). */
    data object Date : CustomFieldType()

    /** Флаг true/false. */
    data object Boolean : CustomFieldType()

    /** Номер телефона. */
    data object Phone : CustomFieldType()

    /** Адрес электронной почты. */
    data object Email : CustomFieldType()

    /** URL-адрес. */
    data object Url : CustomFieldType()

    /** Список допустимых значений; [options] задаёт варианты. */
    data class Select(val options: List<String>) : CustomFieldType()

    /** Возвращает строковый идентификатор типа для хранения в БД. */
    fun typeName(): String =
        when (this) {
            is Text -> "text"
            is Number -> "number"
            is Date -> "date"
            is Boolean -> "boolean"
            is Phone -> "phone"
            is Email -> "email"
            is Url -> "url"
            is Select -> "select"
        }

    /** Сериализует конфигурацию поля в JSON-строку для колонки `config`. */
    fun configJson(): String =
        when (this) {
            is Select -> """{"options":${options.joinToString(",", "[", "]") { "\"$it\"" }}}"""
            else -> "{}"
        }

    companion object {
        /**
         * Восстанавливает [CustomFieldType] из пары ([typeName], [configJson]).
         * Неизвестный тип трактуется как [Text].
         */
        fun parse(typeName: String, configJson: String): CustomFieldType =
            when (typeName) {
                "text" -> Text
                "number" -> Number
                "date" -> Date
                "boolean" -> Boolean
                "phone" -> Phone
                "email" -> Email
                "url" -> Url
                "select" -> {
                    val options = parseSelectOptions(configJson)
                    Select(options)
                }
                else -> Text
            }

        private fun parseSelectOptions(configJson: String): List<String> {
            val trimmed = configJson.trim()
            val optionsStart = trimmed.indexOf("\"options\"")
            if (optionsStart == -1) {
                return emptyList()
            }
            val arrayStart = trimmed.indexOf('[', optionsStart)
            val arrayEnd = trimmed.lastIndexOf(']')
            if (arrayStart == -1 || arrayEnd == -1) {
                return emptyList()
            }
            val arrayContent = trimmed.substring(arrayStart + 1, arrayEnd).trim()
            if (arrayContent.isEmpty()) {
                return emptyList()
            }
            return arrayContent
                .split(',')
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotEmpty() }
        }
    }
}
