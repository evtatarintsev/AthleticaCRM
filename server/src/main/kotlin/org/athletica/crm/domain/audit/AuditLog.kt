package org.athletica.crm.domain.audit

import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.UserId
import org.athletica.crm.storage.Transaction

interface AuditLog {
    context(tr: Transaction)
    suspend fun log(event: AuditEvent)

    context(ctx: RequestContext, tr: Transaction)
    suspend fun list(filter: AuditFilter): List<AuditEvent>

    context(ctx: RequestContext, tr: Transaction)
    suspend fun count(filter: AuditFilter): Long
}

data class AuditFilter(
    val limit: UInt,
    val offset: UInt,
    val actionType: AuditActionType? = null,
    val userId: UserId? = null,
    val entityType: String? = null,
    val from: String? = null,
    val to: String? = null,
)
