package org.athletica.crm.domain.audit

import arrow.core.raise.Raise
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction

interface AuditLog {
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun log(event: AuditEvent)
}
