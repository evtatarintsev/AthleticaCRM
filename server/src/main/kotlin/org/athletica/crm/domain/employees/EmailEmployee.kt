package org.athletica.crm.domain.employees

import arrow.core.raise.Raise
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.mail.OrgEmails
import org.athletica.crm.storage.Transaction

class EmailEmployee(private val delegate: Employee, private val orgEmails: OrgEmails) : Employee by delegate {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun invite(email: EmailAddress, password: String) {
        delegate.invite(email, password)

        val textBody =
            """
            Здравствуйте, $name!

            Администратор предоставил вам доступ в AthleticaCRM.

            Логин: $email
            Пароль: $password

            После входа вы можете сменить пароль в настройках профиля.
            """.trimIndent()

        val htmlBody =
            """
            <p>Здравствуйте, <b>$name</b>!</p>
            <p>Администратор предоставил вам доступ в AthleticaCRM.</p>
            <p><b>Логин:</b> $email<br>
            <b>Пароль:</b> $password</p>
            <p>После входа вы можете сменить пароль в настройках профиля.</p>
            """.trimIndent()

        orgEmails.schedule(
            orgId = ctx.orgId,
            to = listOf(email),
            subject = "Доступ в AthleticaCRM",
            textBody = textBody,
            htmlBody = htmlBody,
        )
    }
}
