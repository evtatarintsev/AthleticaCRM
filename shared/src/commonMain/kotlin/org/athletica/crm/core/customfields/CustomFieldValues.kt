package org.athletica.crm.core.customfields

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError

/**
 * Набор значений кастомных полей, всегда соответствующий определениям.
 * Объект не может находиться в невалидном состоянии: мутации возможны
 * только через [with], который поднимает ошибку при нарушении инварианта.
 */
class CustomFieldValues private constructor(
    /** Определения полей, против которых валидируются значения. */
    val definitions: List<CustomFieldDefinition>,
    private val raw: List<CustomFieldValue>,
) {
    /** Создаёт пустой набор для заданных определений. */
    constructor(definitions: List<CustomFieldDefinition>) : this(definitions, emptyList())

    /** Возвращает значение поля [fieldKey], либо null если оно не задано. */
    operator fun get(fieldKey: CustomFieldKey): CustomFieldValue? = raw.find { it.fieldKey == fieldKey }

    /**
     * Обновить или добавить одно значение.
     * Поднимает ошибку, если [value] ссылается на несуществующее поле или тип не совпадает.
     */
    context(raise: Raise<DomainError>)
    fun with(value: CustomFieldValue): CustomFieldValues = with(listOf(value))

    /**
     * Массовое обновление значений.
     * Поднимает ошибку при первом несоответствии ключа или типа определению.
     */
    context(raise: Raise<DomainError>)
    fun with(values: List<CustomFieldValue>): CustomFieldValues {
        values.forEach { v ->
            val def =
                definitions.find { it.fieldKey == v.fieldKey }
                    ?: raise(CommonDomainError("UNKNOWN_CUSTOM_FIELD", "Неизвестное поле: ${v.fieldKey}"))
            if (!v.matchesType(def)) {
                raise(CommonDomainError("CUSTOM_FIELD_TYPE_MISMATCH", "Неверный тип для поля ${v.fieldKey}"))
            }
        }
        val merged =
            values.fold(raw) { acc, v ->
                if (acc.any { it.fieldKey == v.fieldKey }) {
                    acc.map { if (it.fieldKey == v.fieldKey) v else it }
                } else {
                    acc + v
                }
            }
        return CustomFieldValues(definitions, merged)
    }

    /** Текущий список значений. Все значения гарантированно соответствуют определениям. */
    fun toList(): List<CustomFieldValue> = raw
}

/**
 * Проверяет, что значение совместимо с подтипом определения.
 * Текстовые подтипы (Text/Phone/Email/Url) принимают [CustomFieldValue.Text].
 */
private fun CustomFieldValue.matchesType(def: CustomFieldDefinition): Boolean =
    when (this) {
        is CustomFieldValue.Text ->
            def is CustomFieldDefinition.Text ||
                def is CustomFieldDefinition.Phone ||
                def is CustomFieldDefinition.Email ||
                def is CustomFieldDefinition.Url
        is CustomFieldValue.Number -> def is CustomFieldDefinition.Number
        is CustomFieldValue.Bool -> def is CustomFieldDefinition.Bool
        is CustomFieldValue.Date -> def is CustomFieldDefinition.Date
        is CustomFieldValue.Select -> def is CustomFieldDefinition.Select
    }
