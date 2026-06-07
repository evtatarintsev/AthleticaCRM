package org.athletica.crm.domain.channels

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.storage.Transaction

/** Репозиторий интеграций каналов связи. Все операции изолированы по организации. */
interface ChannelIntegrations {
    /** Возвращает все интеграции организации в порядке создания. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(): List<ChannelIntegration>

    /** Возвращает интеграцию по [id]; `CHANNEL_NOT_FOUND`, если не найдена или принадлежит другой организации. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byId(id: ChannelIntegrationId): ChannelIntegration

    /** Создаёт несохранённую (активную) интеграцию; запись в БД — через [ChannelIntegration.save]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun new(id: ChannelIntegrationId, name: String, config: ChannelConfig): ChannelIntegration

    /**
     * Worker-путь: возвращает конфиг интеграции по [id] без привязки к организации.
     * Используется фоновым диспетчером при отправке. `null`, если интеграция удалена.
     */
    context(tr: Transaction)
    suspend fun configById(id: ChannelIntegrationId): ChannelConfig?
}
