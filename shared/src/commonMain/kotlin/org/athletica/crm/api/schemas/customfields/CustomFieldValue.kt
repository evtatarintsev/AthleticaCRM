package org.athletica.crm.api.schemas.customfields

import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Типизированное значение кастомного поля.
 * [fieldKey] — ключ поля из определения; значение без ключа не имеет смысла.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class CustomFieldValue {
    /** Ключ поля из [CustomFieldDefinitionSchema]. */
    abstract val fieldKey: String

    /** Текстовое значение (fieldType: text, phone, email, url). */
    @Serializable
    @SerialName("text")
    data class Text(
        override val fieldKey: String,
        val value: String,
    ) : CustomFieldValue()

    /** Числовое значение (fieldType: number). */
    @Serializable
    @SerialName("number")
    data class Number(
        override val fieldKey: String,
        val value: Double,
    ) : CustomFieldValue()

    /** Булево значение (fieldType: boolean). */
    @Serializable
    @SerialName("bool")
    data class Bool(
        override val fieldKey: String,
        val value: Boolean,
    ) : CustomFieldValue()

    /** Дата (fieldType: date). */
    @Serializable
    @SerialName("date")
    data class Date(
        override val fieldKey: String,
        val value: LocalDate,
    ) : CustomFieldValue()

    /** Значение из списка вариантов (fieldType: select). */
    @Serializable
    @SerialName("select")
    data class Select(
        override val fieldKey: String,
        val value: String,
    ) : CustomFieldValue()
}
