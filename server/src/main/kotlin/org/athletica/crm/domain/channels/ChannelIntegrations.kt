package org.athletica.crm.domain.channels

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.messaging.ChannelType
import org.athletica.crm.storage.Transaction

/**
 * Настроенная интеграция канала связи организации.
 *
 * [config] хранит провайдер-специфичные настройки (токены, идентификаторы отправителя и т.п.)
 * как пары ключ-значение; их интерпретация — задача конкретного адаптера канала.
 */
data class ChannelIntegration(
    val id: ChannelIntegrationId,
    val channelType: ChannelType,
    val name: String,
    val config: Map<String, String>,
    val enabled: Boolean,
)

/** Репозиторий интеграций каналов связи. Все операции изолированы по организации. */
interface ChannelIntegrations {
    /** Возвращает все интеграции организации в порядке создания. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(): List<ChannelIntegration>

    /** Возвращает интеграцию по [id]; `CHANNEL_NOT_FOUND`, если не найдена или принадлежит другой организации. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byId(id: ChannelIntegrationId): ChannelIntegration

    /** Создаёт интеграцию канала. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun create(integration: ChannelIntegration)

    /** Обновляет имя, конфиг и флаг активности интеграции. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun update(integration: ChannelIntegration)

    /** Удаляет интеграцию по [id]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun delete(id: ChannelIntegrationId)

    /**
     * Worker-путь: возвращает конфиг интеграции по [id] без привязки к организации.
     * Используется фоновым диспетчером при отправке. `null`, если интеграция удалена.
     */
    context(tr: Transaction)
    suspend fun configById(id: ChannelIntegrationId): Map<String, String>?
}
