package org.athletica.crm.core.customfields

/** Тип кастомного поля определяет допустимые значения и правила валидации. */
sealed class CustomFieldType {
    /** Строковый идентификатор типа для хранения в БД. */
    abstract val name: String

    /** Сериализует конфигурацию поля в JSON-строку для колонки `config`. */
    abstract fun configJson(): String

    /** Строковое значение произвольной длины. */
    data object Text : CustomFieldType() {
        override val name = "text"

        override fun configJson() = "{}"
    }

    /** Числовое значение (Double). */
    data object Number : CustomFieldType() {
        override val name = "number"

        override fun configJson() = "{}"
    }

    /** Дата (LocalDate). */
    data object Date : CustomFieldType() {
        override val name = "date"

        override fun configJson() = "{}"
    }

    /** Флаг true/false. */
    data object Boolean : CustomFieldType() {
        override val name = "boolean"

        override fun configJson() = "{}"
    }

    /** Номер телефона. */
    data object Phone : CustomFieldType() {
        override val name = "phone"

        override fun configJson() = "{}"
    }

    /** Адрес электронной почты. */
    data object Email : CustomFieldType() {
        override val name = "email"

        override fun configJson() = "{}"
    }

    /** URL-адрес. */
    data object Url : CustomFieldType() {
        override val name = "url"

        override fun configJson() = "{}"
    }

    /** Список допустимых значений; [options] задаёт варианты. */
    data class Select(val options: List<String>) : CustomFieldType() {
        override val name = "select"

        override fun configJson() = """{"options":${options.joinToString(",", "[", "]") { "\"$it\"" }}}"""

        companion object {
            /** Извлекает список вариантов из JSON-строки колонки `config`. */
            fun parseOptions(configJson: String): List<String> {
                val trimmed = configJson.trim()
                val optionsStart = trimmed.indexOf("\"options\"")
                if (optionsStart == -1) {
                    return emptyList()
                }
                val arrayStart = trimmed.indexOf('[', optionsStart)
                val arrayEnd = trimmed.lastIndexOf(']')
                if (arrayStart == -1 || arrayEnd == -1 || arrayEnd <= arrayStart) {
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
                "select" -> Select(Select.parseOptions(configJson))
                else -> Text
            }
    }
}
