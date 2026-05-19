package org.athletica.crm.api.schemas.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Схема сохранённого пользовательского вида списка.
 * [id] — уникальный идентификатор (UUID v4 в строковом виде или префикс `system:`).
 * [name] — отображаемое имя, заданное пользователем.
 * [filterJson] — произвольный blob фильтра, специфичный для раздела.
 *   Декодирование и кодирование — на стороне ViewModel конкретного раздела.
 * [sort] — сортировка, привязанная к этому виду (опционально).
 */
@Serializable
data class SavedViewSchema(
    val id: String,
    val name: String,
    val filterJson: JsonElement,
    val sort: SortStateSchema? = null,
)
