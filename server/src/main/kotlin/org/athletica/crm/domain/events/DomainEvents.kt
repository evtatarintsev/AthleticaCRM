package org.athletica.crm.domain.events

import org.athletica.crm.core.RequestContext
import org.athletica.crm.storage.Transaction

interface DomainEvents {
    context(ctx: RequestContext, tr: Transaction)
    suspend fun publish(event: DomainEvent)
}
