package org.athletica.crm

import kotlinx.coroutines.channels.Channel
import org.athletica.crm.domain.audit.AuditEvent
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.storage.Transaction

class TestAuditLog : AuditLog {
    val channel = Channel<AuditEvent>(capacity = Channel.BUFFERED)

    context(tr: Transaction)
    override suspend fun log(event: AuditEvent) {
        channel.trySend(event)
    }
}
