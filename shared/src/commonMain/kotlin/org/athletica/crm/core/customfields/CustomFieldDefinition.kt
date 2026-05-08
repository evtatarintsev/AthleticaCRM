package org.athletica.crm.core.customfields

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Определение кастомного поля организации.
 * Сериализуется полиморфно с дискриминатором `fieldType`.
 * Конфигурация конкретного типа (опции, границы) живёт прямо на подтипе,
 * поэтому невозможно собрать определение с несовместимыми друг с другом полями.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("fieldType")
sealed class CustomFieldDefinition {
    /** Иммутабельный машинный ключ (только латинские буквы и подчёркивание). */
    abstract val fieldKey: String

    /** Отображаемое название поля. */
    abstract val label: String

    /** Поле обязательно для заполнения. */
    abstract val isRequired: Boolean

    /** Поле участвует в поиске. */
    abstract val isSearchable: Boolean

    /** Поле доступно для сортировки. */
    abstract val isSortable: Boolean

    /** Произвольная строка с опциональными ограничениями длины. */
    @Serializable
    @SerialName("text")
    data class Text(
        override val fieldKey: String,
        override val label: String,
        override val isRequired: Boolean = false,
        override val isSearchable: Boolean = false,
        override val isSortable: Boolean = false,
        /** Минимальная длина строки, либо null если не задано. */
        val minLength: Int? = null,
        /** Максимальная длина строки, либо null если не задано. */
        val maxLength: Int? = null,
    ) : CustomFieldDefinition()

    /** Числовое значение с опциональными границами. */
    @Serializable
    @SerialName("number")
    data class Number(
        override val fieldKey: String,
        override val label: String,
        override val isRequired: Boolean = false,
        override val isSearchable: Boolean = false,
        override val isSortable: Boolean = false,
        /** Минимальное допустимое значение, либо null если не задано. */
        val minValue: Long? = null,
        /** Максимальное допустимое значение, либо null если не задано. */
        val maxValue: Long? = null,
    ) : CustomFieldDefinition()

    /** Календарная дата (без времени). */
    @Serializable
    @SerialName("date")
    data class Date(
        override val fieldKey: String,
        override val label: String,
        override val isRequired: Boolean = false,
        override val isSearchable: Boolean = false,
        override val isSortable: Boolean = false,
    ) : CustomFieldDefinition()

    /** Логический флаг true/false. */
    @Serializable
    @SerialName("boolean")
    data class Bool(
        override val fieldKey: String,
        override val label: String,
        override val isRequired: Boolean = false,
        override val isSearchable: Boolean = false,
        override val isSortable: Boolean = false,
    ) : CustomFieldDefinition()

    /** Номер телефона (хранится как строка). */
    @Serializable
    @SerialName("phone")
    data class Phone(
        override val fieldKey: String,
        override val label: String,
        override val isRequired: Boolean = false,
        override val isSearchable: Boolean = false,
        override val isSortable: Boolean = false,
    ) : CustomFieldDefinition()

    /** Адрес электронной почты (хранится как строка). */
    @Serializable
    @SerialName("email")
    data class Email(
        override val fieldKey: String,
        override val label: String,
        override val isRequired: Boolean = false,
        override val isSearchable: Boolean = false,
        override val isSortable: Boolean = false,
    ) : CustomFieldDefinition()

    /** URL-адрес (хранится как строка). */
    @Serializable
    @SerialName("url")
    data class Url(
        override val fieldKey: String,
        override val label: String,
        override val isRequired: Boolean = false,
        override val isSearchable: Boolean = false,
        override val isSortable: Boolean = false,
    ) : CustomFieldDefinition()

    /** Выбор из фиксированного списка вариантов. */
    @Serializable
    @SerialName("select")
    data class Select(
        override val fieldKey: String,
        override val label: String,
        override val isRequired: Boolean = false,
        override val isSearchable: Boolean = false,
        override val isSortable: Boolean = false,
        /** Допустимые варианты значений. */
        val options: List<String> = emptyList(),
    ) : CustomFieldDefinition()
}
