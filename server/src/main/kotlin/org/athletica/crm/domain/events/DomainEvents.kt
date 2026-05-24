package org.athletica.crm.domain.events

import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.storage.Transaction

interface DomainEvents {
    context(ctx: EmployeeRequestContext, tr: Transaction)
    suspend fun publish(event: DomainEvent)
}
