package org.athletica.crm

import kotlinx.coroutines.channels.Channel
import org.athletica.crm.domain.audit.AuditEvent
import org.athletica.crm.domain.audit.AuditLog

class TestAuditLog : AuditLog {
    val channel = Channel<AuditEvent>(capacity = Channel.BUFFERED)

    override fun log(event: AuditEvent) {
        channel.trySend(event)
    }
}
