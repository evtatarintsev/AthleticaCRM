package org.athletica.crm.api.schemas.customfields

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
    val definitions: List<CustomFieldDefinitionSchema>,
    private val raw: List<CustomFieldValue>,
) {
    /** Создаёт пустой набор для заданных определений. */
    constructor(definitions: List<CustomFieldDefinitionSchema>) : this(definitions, emptyList())

    /** Возвращает значение поля [fieldKey], либо null если оно не задано. */
    operator fun get(fieldKey: String): CustomFieldValue? = raw.find { it.fieldKey == fieldKey }

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
            if (!v.matchesType(def.fieldType)) {
                return CommonDomainError(
                    "CUSTOM_FIELD_TYPE_MISMATCH",
                    "Неверный тип для поля ${v.fieldKey}: ожидается ${def.fieldType}",
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

private fun CustomFieldValue.matchesType(fieldType: String): Boolean =
    when (this) {
        is CustomFieldValue.Text -> fieldType in setOf("text", "phone", "email", "url")
        is CustomFieldValue.Number -> fieldType == "number"
        is CustomFieldValue.Bool -> fieldType == "boolean"
        is CustomFieldValue.Date -> fieldType == "date"
        is CustomFieldValue.Select -> fieldType == "select"
    }
