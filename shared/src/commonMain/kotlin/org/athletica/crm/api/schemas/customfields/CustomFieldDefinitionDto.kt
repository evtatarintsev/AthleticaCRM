package org.athletica.crm.api.schemas.customfields

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * DTO определения кастомного поля.
 * Используется как в ответах API, так и в теле запроса на сохранение.
 */
@Serializable
data class CustomFieldDefinitionDto(
    /** Иммутабельный машинный ключ (только латинские буквы и подчёркивание). */
    val fieldKey: String,
    /** Отображаемое название поля. */
    val label: String,
    /**
     * Тип поля: `text`, `number`, `date`, `select`, `boolean`, `phone`, `email`, `url`.
     */
    val fieldType: String,
    /** Конфигурация, зависящая от типа. Для `select` содержит `{"options": [...]}`. */
    val config: JsonObject,
    /** Поле обязательно для заполнения. */
    val isRequired: Boolean,
    /** Поле участвует в поиске. */
    val isSearchable: Boolean,
    /** Поле доступно для сортировки. */
    val isSortable: Boolean,
)
