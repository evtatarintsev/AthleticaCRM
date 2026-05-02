package org.athletica.crm.api.schemas.customfields

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Типизированная конфигурация для разных типов кастомных полей.
 * Используется во фронтенде для безопасной работы с параметрами типа.
 */
sealed class CustomFieldTypeConfig {
    data class SelectConfig(val options: List<String> = emptyList()) : CustomFieldTypeConfig()

    data class NumberConfig(val minValue: Long? = null, val maxValue: Long? = null) : CustomFieldTypeConfig()

    data class TextConfig(val minLength: Int? = null, val maxLength: Int? = null) : CustomFieldTypeConfig()

    object DateConfig : CustomFieldTypeConfig()

    object DefaultConfig : CustomFieldTypeConfig()
}

/**
 * Преобразует JsonObject (с бэкенда) в типизированную конфигурацию на основе типа поля.
 */
fun JsonObject.toTypeConfig(fieldType: String): CustomFieldTypeConfig =
    when (fieldType) {
        "select" -> {
            val options =
                try {
                    this["options"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            CustomFieldTypeConfig.SelectConfig(options)
        }
        "number" -> {
            val minValue =
                try {
                    this["minValue"]?.jsonPrimitive?.content?.toLongOrNull()
                } catch (e: Exception) {
                    null
                }
            val maxValue =
                try {
                    this["maxValue"]?.jsonPrimitive?.content?.toLongOrNull()
                } catch (e: Exception) {
                    null
                }
            CustomFieldTypeConfig.NumberConfig(minValue, maxValue)
        }
        "text" -> {
            val minLength =
                try {
                    this["minLength"]?.jsonPrimitive?.content?.toIntOrNull()
                } catch (e: Exception) {
                    null
                }
            val maxLength =
                try {
                    this["maxLength"]?.jsonPrimitive?.content?.toIntOrNull()
                } catch (e: Exception) {
                    null
                }
            CustomFieldTypeConfig.TextConfig(minLength, maxLength)
        }
        "date" -> CustomFieldTypeConfig.DateConfig
        else -> CustomFieldTypeConfig.DefaultConfig
    }

/**
 * Преобразует типизированную конфигурацию в JsonObject для отправки на бэкенд.
 */
fun CustomFieldTypeConfig.toJson(): JsonObject =
    buildJsonObject {
        when (this@toJson) {
            is CustomFieldTypeConfig.SelectConfig -> {
                put(
                    "options",
                    buildJsonArray {
                        this@toJson.options.forEach { option ->
                            add(JsonPrimitive(option))
                        }
                    },
                )
            }
            is CustomFieldTypeConfig.NumberConfig -> {
                this@toJson.minValue?.let { put("minValue", it) }
                this@toJson.maxValue?.let { put("maxValue", it) }
            }
            is CustomFieldTypeConfig.TextConfig -> {
                this@toJson.minLength?.let { put("minLength", it) }
                this@toJson.maxLength?.let { put("maxLength", it) }
            }
            CustomFieldTypeConfig.DateConfig -> {} // no config
            CustomFieldTypeConfig.DefaultConfig -> {} // no config
        }
    }
