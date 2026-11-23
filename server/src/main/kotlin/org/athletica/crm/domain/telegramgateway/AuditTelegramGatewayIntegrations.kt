package org.athletica.crm.domain.telegramgateway

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logCreate
import org.athletica.crm.domain.audit.logUpdate
import org.athletica.crm.storage.Transaction

/**
 * Декоратор [TelegramGatewayIntegrations], пишущий в аудит подключение, замену токена и
 * включённость. Идентификатор записи — организация; токен в запись не попадает.
 */
class AuditTelegramGatewayIntegrations(
    private val delegate: TelegramGatewayIntegrations,
    private val audit: AuditLog,
) : TelegramGatewayIntegrations by delegate {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun saveToken(token: String) {
        val connected = delegate.current() != null
        delegate.saveToken(token)
        if (connected) {
            audit.logUpdate(ENTITY, ctx.orgId, """{"tokenReplaced":true}""")
        } else {
            audit.logCreate(ENTITY, ctx.orgId, """{"enabled":true}""")
        }
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun setEnabled(enabled: Boolean) {
        delegate.setEnabled(enabled)
        audit.logUpdate(ENTITY, ctx.orgId, """{"enabled":$enabled}""")
    }

    private companion object {
        /** Тип сущности в аудите. */
        const val ENTITY = "telegram_gateway_integration"
    }
}
