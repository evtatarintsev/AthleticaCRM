package org.athletica.infra.mail

interface Mailbox {
    suspend fun send(email: Email)
}
