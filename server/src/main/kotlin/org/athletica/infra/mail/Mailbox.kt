package org.athletica.infra.mail

interface Mailbox {
    suspend fun send(email: Email) // TODO: Не бросать исключения, Возвращать Either<Error, Unit>
}
