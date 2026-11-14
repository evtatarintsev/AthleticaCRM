package org.athletica.crm.core.customfields

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError

/**
 * Набор значений кастомных полей. Новые и изменённые значения проверяются по текущим
 * определениям. При редактировании неизменённые значения могут относиться к прежним определениям.
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
    context(raise: Raise<AppError>)
    fun with(value: CustomFieldValue): CustomFieldValues = with(listOf(value))

    /**
     * Массовое обновление значений.
     * Поднимает ошибку при первом несоответствии ключа или типа определению.
     */
    context(raise: Raise<AppError>)
    fun with(values: List<CustomFieldValue>): CustomFieldValues {
        values.forEach { validate(it) }
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

    /** Проверяет значения и наличие обязательных полей при создании записи. */
    context(raise: Raise<AppError>)
    fun forCreation(values: List<CustomFieldValue>): List<CustomFieldValue> {
        val created = with(values)
        definitions.filter { it.isRequired }.forEach { def ->
            if (!created[def.fieldKey].isFilled()) {
                raise(requiredField(def.fieldKey))
            }
        }
        return created.toList()
    }

    /** Проверяет полную замену полей, сохраняя неизменённые значения по старым определениям. */
    context(raise: Raise<AppError>)
    fun forReplacement(previous: List<CustomFieldValue>, values: List<CustomFieldValue>): List<CustomFieldValue> {
        val stored = previous.associateBy { it.fieldKey }
        val requested = values.associateBy { it.fieldKey }
        values.filter { it != stored[it.fieldKey] }.forEach { validate(it) }
        definitions.filter { it.isRequired }.forEach { def ->
            if (stored[def.fieldKey].isFilled() && !requested[def.fieldKey].isFilled()) {
                raise(requiredField(def.fieldKey))
            }
        }
        return requested.values.toList()
    }

    context(raise: Raise<AppError>)
    private fun validate(value: CustomFieldValue) {
        val key = value.fieldKey
        val def =
            definitions.find { it.fieldKey == key }
                ?: raise(CommonDomainError("UNKNOWN_CUSTOM_FIELD", "Неизвестное поле: $key"))
        if (!value.matchesType(def)) {
            raise(CommonDomainError("CUSTOM_FIELD_TYPE_MISMATCH", "Неверный тип для поля $key"))
        }
        when {
            def is CustomFieldDefinition.Select && value is CustomFieldValue.Select && value.value !in def.options ->
                raise(CommonDomainError("CUSTOM_FIELD_INVALID_OPTION", "Значение «${value.value}» не входит в варианты поля $key"))
            def is CustomFieldDefinition.Text &&
                value is CustomFieldValue.Text &&
                ((def.minLength != null && value.value.length < def.minLength) || (def.maxLength != null && value.value.length > def.maxLength)) ->
                raise(CommonDomainError("CUSTOM_FIELD_OUT_OF_RANGE", "Недопустимая длина поля $key"))
            def is CustomFieldDefinition.Number &&
                value is CustomFieldValue.Number &&
                (!value.value.isFinite() || (def.minValue != null && value.value < def.minValue) || (def.maxValue != null && value.value > def.maxValue)) ->
                raise(CommonDomainError("CUSTOM_FIELD_OUT_OF_RANGE", "Значение поля $key вне допустимого диапазона"))
        }
    }

    /** Текущий список значений. */
    fun toList(): List<CustomFieldValue> = raw
}

private fun requiredField(key: CustomFieldKey) = CommonDomainError("CUSTOM_FIELD_REQUIRED", "Заполните обязательное поле $key")

private fun CustomFieldValue?.isFilled(): Boolean =
    when (this) {
        null -> false
        is CustomFieldValue.Text -> value.isNotBlank()
        is CustomFieldValue.Select -> value.isNotBlank()
        else -> true
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
