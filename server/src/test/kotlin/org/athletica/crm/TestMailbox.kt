package org.athletica.crm

import kotlinx.coroutines.channels.Channel
import org.athletica.crm.domain.mail.Mailbox
import org.athletica.infra.mail.Email

class TestMailbox : Mailbox {
    val inbox = Channel<Email>()

    override suspend fun send(email: Email) {
        inbox.send(email)
    }
}
