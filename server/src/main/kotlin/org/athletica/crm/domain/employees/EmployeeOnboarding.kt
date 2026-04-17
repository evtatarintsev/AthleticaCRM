package org.athletica.crm.domain.employees

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.EmployeeId
import org.athletica.crm.core.OrgEmailId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.auth.Users
import org.athletica.crm.domain.mail.OrgEmail
import org.athletica.crm.domain.mail.OrgEmails
import org.athletica.crm.storage.Transaction

/**
 * Application service: координирует создание учётной записи и отправку письма доступа сотруднику.
 * Все операции с БД выполняются в одной транзакции — письмо сохраняется атомарно с изменением Employee.
 */
class EmployeeOnboarding(
    private val employees: Employees,
    private val users: Users,
    private val orgEmails: OrgEmails,
) {
    /**
     * Выдаёт доступ существующему сотруднику [id]:
     * - создаёт учётную запись с переданным [email] и [password]
     * - активирует сотрудника (is_active = true, user_id заполняется)
     * - планирует отправку письма с реквизитами (в той же транзакции)
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun register(id: EmployeeId, email: EmailAddress, password: String) {
        val user = users.new(email.value, password)
        employees
            .byId(id)
            .withUserId(user.id)
            .activate()
            .also {
                scheduleAccessEmail(name = it.name, email = email, password = password)
            }
            .save()
    }

    context(ctx: RequestContext, tr: Transaction)
    private suspend fun scheduleAccessEmail(name: String, email: EmailAddress, password: String) {
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
            OrgEmail(
                id = OrgEmailId.new(),
                orgId = ctx.orgId,
                to = listOf(email),
                subject = "Доступ в AthleticaCRM",
                textBody = textBody,
                htmlBody = htmlBody,
            ),
        )
    }
}
