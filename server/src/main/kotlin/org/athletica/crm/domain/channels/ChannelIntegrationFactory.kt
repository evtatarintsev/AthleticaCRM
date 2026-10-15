package org.athletica.crm.domain.channels

import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.messaging.ChannelConfig

/**
 * Строит объект-интеграцию из сохранённых данных, выбирая реализацию по подтипу [ChannelConfig].
 *
 * Единственная точка сопоставления «вид конфига → адаптер провайдера»; используется только при
 * гидрации интеграции из БД. Реализация — в `infra.messaging`, чтобы добавление провайдера не
 * затрагивало домен.
 */
interface ChannelIntegrationFactory {
    /** Собирает интеграцию [id] с настройками [config] в конкретную реализацию канала. */
    fun create(
        id: ChannelIntegrationId,
        name: String,
        config: ChannelConfig,
        enabled: Boolean,
    ): ChannelIntegration
}
