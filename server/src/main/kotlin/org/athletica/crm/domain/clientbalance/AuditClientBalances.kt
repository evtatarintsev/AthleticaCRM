package org.athletica.crm.domain.clientbalance

import arrow.core.raise.context.Raise
import kotlinx.serialization.json.Json
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.money.Money
import org.athletica.crm.domain.audit.AuditActionType
import org.athletica.crm.domain.audit.AuditEvent
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.clients.ActiveClient
import org.athletica.crm.storage.Transaction

class AuditClientBalances(private val delegate: ClientBalances, private val audit: AuditLog) : ClientBalances by delegate {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun adjust(client: ActiveClient, amount: Money, note: String) =
        delegate.adjust(client, amount, note).also {
            audit.log(
                AuditEvent(
                    orgId = ctx.orgId,
                    userId = ctx.userId,
                    username = ctx.username,
                    actionType = AuditActionType.BALANCE_ADJUST,
                    ipAddress = ctx.clientIp,
                    entityType = "client",
                    entityId = client.id.value,
                    data = """{"amount":${amount.minorUnits},"currency":"${amount.currency.code}","note":${Json.encodeToString(note)}}""",
                ),
            )
        }
}
