package org.athletica.crm.api.schemas.channels

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.messaging.ChannelType

/** Настроенная интеграция канала связи организации. */
@Serializable
data class ChannelIntegrationDto(
    val id: ChannelIntegrationId,
    val channelType: ChannelType,
    val name: String,
    val config: Map<String, String>,
    val enabled: Boolean,
)

/** Список интеграций каналов организации. */
@Serializable
data class ChannelListResponse(
    val channels: List<ChannelIntegrationDto>,
)

/** Запрос на создание интеграции канала. */
@Serializable
data class CreateChannelIntegrationRequest(
    val id: ChannelIntegrationId,
    val channelType: ChannelType,
    val name: String,
    val config: Map<String, String> = emptyMap(),
)

/** Запрос на обновление интеграции канала. */
@Serializable
data class UpdateChannelIntegrationRequest(
    val id: ChannelIntegrationId,
    val name: String,
    val config: Map<String, String> = emptyMap(),
    val enabled: Boolean,
)

/** Запрос на удаление интеграции канала. */
@Serializable
data class DeleteChannelIntegrationRequest(
    val id: ChannelIntegrationId,
)
