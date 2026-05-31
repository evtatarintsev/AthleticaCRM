package org.athletica.crm.domain.channels

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logCreate
import org.athletica.crm.domain.audit.logDelete
import org.athletica.crm.domain.audit.logUpdate
import org.athletica.crm.storage.Transaction

/**
 * Декоратор [ChannelIntegrations], логирующий изменения в аудит.
 * В payload не попадает [ChannelIntegration.config], так как он содержит секреты провайдеров.
 */
class AuditChannelIntegrations(
    private val delegate: ChannelIntegrations,
    private val audit: AuditLog,
) : ChannelIntegrations by delegate {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun create(integration: ChannelIntegration) =
        delegate.create(integration).also {
            audit.logCreate("channel_integration", integration.id, integration.auditPayload())
        }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun update(integration: ChannelIntegration) =
        delegate.update(integration).also {
            audit.logUpdate("channel_integration", integration.id, integration.auditPayload())
        }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun delete(id: ChannelIntegrationId) =
        delegate.delete(id).also {
            audit.logDelete("channel_integration", id, "")
        }

    private fun ChannelIntegration.auditPayload(): String = """{"name":"$name","channelType":"$channelType","enabled":$enabled}"""
}
