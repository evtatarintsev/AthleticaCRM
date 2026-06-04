package org.athletica.crm.api.schemas.channels

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.core.messaging.ChannelType

/**
 * Настроенная интеграция канала связи организации.
 * [channelType] выводится из провайдера [config] и отдаётся клиенту для группировки/фильтрации.
 */
@Serializable
data class ChannelIntegrationSchema(
    val id: ChannelIntegrationId,
    val channelType: ChannelType,
    val name: String,
    val config: ChannelConfig,
    val enabled: Boolean,
)

/** Список интеграций каналов организации. */
@Serializable
data class ChannelListResponse(
    val channels: List<ChannelIntegrationSchema>,
)

/** Запрос на создание интеграции канала. Тип канала определяется провайдером [config]. */
@Serializable
data class CreateChannelIntegrationRequest(
    val id: ChannelIntegrationId,
    val name: String,
    val config: ChannelConfig,
)

/** Запрос на обновление интеграции канала. */
@Serializable
data class UpdateChannelIntegrationRequest(
    val id: ChannelIntegrationId,
    val name: String,
    val config: ChannelConfig,
    val enabled: Boolean,
)

/** Запрос на удаление интеграции канала. */
@Serializable
data class DeleteChannelIntegrationRequest(
    val id: ChannelIntegrationId,
)
