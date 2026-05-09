package org.athletica.crm.core.customfields

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError

/**
 * Набор значений кастомных полей, всегда соответствующий определениям.
 * Объект не может находиться в невалидном состоянии: мутации возможны
 * только через [with], который возвращает ошибку при нарушении инварианта.
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
     * Возвращает ошибку, если [value] ссылается на несуществующее поле или тип не совпадает.
     */
    fun with(value: CustomFieldValue): Either<DomainError, CustomFieldValues> = with(listOf(value))

    /**
     * Массовое обновление значений.
     * Возвращает ошибку при первом несоответствии ключа или типа определению.
     */
    fun with(values: List<CustomFieldValue>): Either<DomainError, CustomFieldValues> {
        for (v in values) {
            val def =
                definitions.find { it.fieldKey == v.fieldKey }
                    ?: return CommonDomainError("UNKNOWN_CUSTOM_FIELD", "Неизвестное поле: ${v.fieldKey}").left()
            if (!v.matchesType(def)) {
                return CommonDomainError(
                    "CUSTOM_FIELD_TYPE_MISMATCH",
                    "Неверный тип для поля ${v.fieldKey}",
                ).left()
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
        return CustomFieldValues(definitions, merged).right()
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
