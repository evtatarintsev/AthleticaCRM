package org.athletica.crm.core.messaging

import kotlinx.serialization.json.Json

/** JSON-представление конфига для хранения в jsonb-колонке `channel_integrations.config`. */
fun ChannelConfig.toJson(): String = Json.encodeToString(ChannelConfig.serializer(), this)

/**
 * Конфиг канала из JSON-представления jsonb-колонки. Единая точка разбора для домена и read-проекций,
 * чтобы настройки десериализации не расходились.
 */
fun String.toChannelConfig(): ChannelConfig = Json.decodeFromString(ChannelConfig.serializer(), this)
