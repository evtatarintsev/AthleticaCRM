package org.athletica.crm.domain.mail

import org.athletica.infra.mail.Email

interface Mailbox {
    suspend fun send(email: Email) // TODO: Не бросать исключения, Возвращать Either<Error, Unit>
}
