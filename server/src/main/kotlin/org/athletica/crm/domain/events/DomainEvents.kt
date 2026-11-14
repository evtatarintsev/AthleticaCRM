package org.athletica.crm.domain.events

import arrow.core.raise.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction

interface DomainEvents {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun publish(event: DomainEvent)
}
