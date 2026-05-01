package org.athletica.crm.core.customfields

import kotlinx.datetime.LocalDate

/**
 * Ядро кастомных полей: валидация, сериализация и десериализация значений.
 * Не зависит от домена, БД или RequestContext.
 * Создаётся с набором определений конкретной организации и типа сущности.
 */
class CustomFields(private val definitions: List<CustomFieldDefinition>) {
    /**
     * Валидирует [raw] — словарь сырых строковых значений — против определений.
     * Возвращает список ошибок; пустой список означает успех.
     */
    fun validate(raw: Map<String, String?>): List<CustomFieldError> {
        val errors = mutableListOf<CustomFieldError>()
        definitions.forEach { def ->
            val value = raw[def.fieldKey]
            if (value == null || value.isBlank()) {
                if (def.isRequired) {
                    errors.add(CustomFieldError(def.fieldKey, "REQUIRED"))
                }
                return@forEach
            }
            when (def.fieldType) {
                is CustomFieldType.Number -> {
                    if (value.toDoubleOrNull() == null) {
                        errors.add(CustomFieldError(def.fieldKey, "INVALID_TYPE"))
                    }
                }
                is CustomFieldType.Date -> {
                    if (runCatching { LocalDate.parse(value) }.isFailure) {
                        errors.add(CustomFieldError(def.fieldKey, "INVALID_TYPE"))
                    }
                }
                is CustomFieldType.Boolean -> {
                    if (value != "true" && value != "false") {
                        errors.add(CustomFieldError(def.fieldKey, "INVALID_TYPE"))
                    }
                }
                is CustomFieldType.Select -> {
                    if (value !in def.fieldType.options) {
                        errors.add(CustomFieldError(def.fieldKey, "UNKNOWN_OPTION"))
                    }
                }
                else -> Unit
            }
        }
        return errors
    }

    /**
     * Десериализует словарь сырых строковых значений в типизированные [CustomFieldValue].
     * Неизвестные ключи и недопустимые значения молча отбрасываются.
     */
    fun deserialize(raw: Map<String, String?>): Map<String, CustomFieldValue> {
        val result = mutableMapOf<String, CustomFieldValue>()
        definitions.forEach { def ->
            val value = raw[def.fieldKey]
            if (value == null || value.isBlank()) {
                result[def.fieldKey] = CustomFieldValue.Empty
                return@forEach
            }
            result[def.fieldKey] =
                when (def.fieldType) {
                    is CustomFieldType.Text, is CustomFieldType.Phone,
                    is CustomFieldType.Email, is CustomFieldType.Url,
                    -> CustomFieldValue.TextValue(value)
                    is CustomFieldType.Number -> {
                        val num = value.toDoubleOrNull()
                        if (num != null) CustomFieldValue.NumberValue(num) else CustomFieldValue.Empty
                    }
                    is CustomFieldType.Date -> {
                        runCatching { CustomFieldValue.DateValue(LocalDate.parse(value)) }
                            .getOrElse { CustomFieldValue.Empty }
                    }
                    is CustomFieldType.Boolean -> {
                        when (value) {
                            "true" -> CustomFieldValue.BooleanValue(true)
                            "false" -> CustomFieldValue.BooleanValue(false)
                            else -> CustomFieldValue.Empty
                        }
                    }
                    is CustomFieldType.Select -> {
                        if (value in def.fieldType.options) {
                            CustomFieldValue.SelectValue(value)
                        } else {
                            CustomFieldValue.Empty
                        }
                    }
                }
        }
        return result
    }

    /**
     * Сериализует типизированные значения в словарь строковых значений для хранения.
     */
    fun serialize(values: Map<String, CustomFieldValue>): Map<String, String?> =
        values.mapValues { (_, v) ->
            when (v) {
                is CustomFieldValue.TextValue -> v.value
                is CustomFieldValue.NumberValue -> v.value.toString()
                is CustomFieldValue.DateValue -> v.value.toString()
                is CustomFieldValue.BooleanValue -> v.value.toString()
                is CustomFieldValue.SelectValue -> v.value
                is CustomFieldValue.Empty -> null
            }
        }
}
