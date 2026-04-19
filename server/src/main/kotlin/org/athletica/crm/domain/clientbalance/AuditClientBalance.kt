package org.athletica.crm.domain.clientbalance

import arrow.core.raise.context.Raise
import kotlinx.serialization.json.Json
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.audit.AuditActionType
import org.athletica.crm.domain.audit.AuditEvent
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.storage.Transaction

class AuditClientBalance(private val delegate: ClientBalance, private val audit: AuditLog) : ClientBalance by delegate {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun adjust(amount: Double, note: String) =
        delegate.adjust(amount, note).also {
            audit.log(
                AuditEvent(
                    orgId = ctx.orgId,
                    userId = ctx.userId,
                    username = ctx.username,
                    actionType = AuditActionType.BALANCE_ADJUST,
                    ipAddress = ctx.clientIp,
                    entityType = "client",
                    entityId = clientId.value,
                    data = """{"amount":$amount,"note":${Json.encodeToString(note)}}""",
                ),
            )
        }
}
