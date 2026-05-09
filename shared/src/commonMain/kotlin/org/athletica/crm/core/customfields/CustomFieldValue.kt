package org.athletica.crm.core.customfields

import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Типизированное значение кастомного поля.
 * [fieldKey] — ключ поля из [CustomFieldDefinition]; значение без ключа не имеет смысла.
 * Сериализуется полиморфно с дискриминатором `type`.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class CustomFieldValue {
    /** Ключ поля, к которому относится значение. */
    abstract val fieldKey: CustomFieldKey

    /** Текстовое значение (для text, phone, email, url). */
    @Serializable
    @SerialName("text")
    data class Text(
        override val fieldKey: CustomFieldKey,
        val value: String,
    ) : CustomFieldValue()

    /** Числовое значение. */
    @Serializable
    @SerialName("number")
    data class Number(
        override val fieldKey: CustomFieldKey,
        val value: Double,
    ) : CustomFieldValue()

    /** Логическое значение. */
    @Serializable
    @SerialName("bool")
    data class Bool(
        override val fieldKey: CustomFieldKey,
        val value: Boolean,
    ) : CustomFieldValue()

    /** Дата. */
    @Serializable
    @SerialName("date")
    data class Date(
        override val fieldKey: CustomFieldKey,
        val value: LocalDate,
    ) : CustomFieldValue()

    /** Значение из списка вариантов. */
    @Serializable
    @SerialName("select")
    data class Select(
        override val fieldKey: CustomFieldKey,
        val value: String,
    ) : CustomFieldValue()
}
