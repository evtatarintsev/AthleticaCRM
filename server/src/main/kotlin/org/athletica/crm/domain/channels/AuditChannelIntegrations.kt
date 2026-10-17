package org.athletica.crm.domain.channels

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logDelete
import org.athletica.crm.domain.audit.logUpdate
import org.athletica.crm.storage.Transaction

/**
 * Декоратор [ChannelIntegrations], логирующий изменения интеграций (добавление, обновление,
 * включение и выключение, удаление).
 * Запросы [byId], [config] и worker-путь [senderById] делегируются без обёртки.
 * В payload не попадает [ChannelConfig] — он содержит секреты провайдеров.
 */
class AuditChannelIntegrations(
    private val delegate: ChannelIntegrations,
    private val audit: AuditLog,
) : ChannelIntegrations by delegate {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun add(id: ChannelIntegrationId, name: String, config: ChannelConfig) {
        delegate.add(id, name, config)
        audit.logUpdate("channel_integration", id, """{"name":"$name","kind":"${config::class.simpleName}","enabled":true}""")
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun update(
        id: ChannelIntegrationId,
        name: String,
        config: ChannelConfig,
    ) {
        delegate.update(id, name, config)
        audit.logUpdate("channel_integration", id, """{"name":"$name","kind":"${config::class.simpleName}"}""")
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun setEnabled(id: ChannelIntegrationId, enabled: Boolean) {
        delegate.setEnabled(id, enabled)
        audit.logUpdate("channel_integration", id, """{"enabled":$enabled}""")
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun remove(id: ChannelIntegrationId) {
        delegate.remove(id)
        audit.logDelete("channel_integration", id, "")
    }
}
