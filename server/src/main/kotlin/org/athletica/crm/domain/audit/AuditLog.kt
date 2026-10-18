package org.athletica.crm.domain.audit

import org.athletica.crm.storage.Transaction

interface AuditLog {
    context(tr: Transaction)
    suspend fun log(event: AuditEvent)
}
