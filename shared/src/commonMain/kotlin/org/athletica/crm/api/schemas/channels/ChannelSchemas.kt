package org.athletica.crm.api.schemas.channels

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.messaging.ChannelConfig

/**
 * Настроенная интеграция канала связи организации.
 * Вид канала и его возможности выводятся из [config] (его подтипа).
 */
@Serializable
data class ChannelIntegrationSchema(
    val id: ChannelIntegrationId,
    val name: String,
    val config: ChannelConfig,
    val enabled: Boolean,
    /** Ссылка на бота вида `https://t.me/<username>`; `null` для каналов без ссылки. */
    val botLink: String?,
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
