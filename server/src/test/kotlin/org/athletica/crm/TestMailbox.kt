package org.athletica.crm

import kotlinx.coroutines.channels.Channel
import org.athletica.infra.mail.Email
import org.athletica.infra.mail.Mailbox

class TestMailbox : Mailbox {
    val inbox = Channel<Email>()
    override suspend fun send(email: Email) {
        inbox.send(email)
    }
}
