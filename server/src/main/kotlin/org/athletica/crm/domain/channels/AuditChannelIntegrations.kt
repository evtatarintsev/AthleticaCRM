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
 * Декоратор [ChannelIntegrations], оборачивающий выдаваемые интеграции в [AuditChannelIntegration].
 * Worker-путь [configById] наследуется без обёртки.
 */
class AuditChannelIntegrations(
    private val delegate: ChannelIntegrations,
    private val audit: AuditLog,
) : ChannelIntegrations by delegate {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list() = delegate.list().map { AuditChannelIntegration(it, audit) }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: ChannelIntegrationId) = AuditChannelIntegration(delegate.byId(id), audit)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(id: ChannelIntegrationId, name: String, config: ChannelConfig) = AuditChannelIntegration(delegate.new(id, name, config), audit)
}

/**
 * Декоратор [ChannelIntegration], логирующий сохранение и удаление.
 * В payload не попадает [ChannelIntegration.config], так как он содержит секреты провайдеров.
 */
class AuditChannelIntegration(
    private val delegate: ChannelIntegration,
    private val audit: AuditLog,
) : ChannelIntegration by delegate {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun save() =
        delegate.save().also {
            audit.logUpdate("channel_integration", id, auditPayload())
        }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun delete() =
        delegate.delete().also {
            audit.logDelete("channel_integration", id, "")
        }

    override fun withNew(name: String, config: ChannelConfig, enabled: Boolean) = AuditChannelIntegration(delegate.withNew(name, config, enabled), audit)

    private fun auditPayload(): String = """{"name":"$name","channelType":"$channelType","enabled":$enabled}"""
}
