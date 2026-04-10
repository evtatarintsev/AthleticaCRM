package org.athletica.crm.usecases.employees

import arrow.core.Either
import arrow.core.raise.either
import org.athletica.crm.api.schemas.employees.SendEmployeeAccessRequest
import org.athletica.crm.audit.AuditLog
import org.athletica.crm.audit.logCreate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import org.athletica.crm.i18n.Messages
import org.athletica.crm.security.PasswordHasher
import org.athletica.infra.mail.Email
import org.athletica.infra.mail.EmailAddress
import org.athletica.infra.mail.EmailHtmlBody
import org.athletica.infra.mail.EmailTextBody
import org.athletica.infra.mail.Mailbox
import org.athletica.infra.mail.Subject
import kotlin.uuid.toJavaUuid

context(db: Database, ctx: RequestContext, audit: AuditLog, passwordHasher: PasswordHasher, mailbox: Mailbox)
suspend fun sendEmployeeAccess(request: SendEmployeeAccessRequest): Either<CommonDomainError, Unit> =
    either {
        // Загружаем сотрудника и связанного пользователя
        val row =
            db
                .sql(
                    """
                    SELECT e.email, e.name, u.id AS user_id
                    FROM employees e
                    JOIN users u ON u.id = e.user_id
                    WHERE e.id = :employeeId AND e.org_id = :orgId
                    """.trimIndent(),
                ).bind("employeeId", request.employeeId.toJavaUuid())
                .bind("orgId", ctx.orgId.value)
                .list { row, _ ->
                    Triple(
                        row.get("email", String::class.java),
                        row.get("name", String::class.java),
                        row.get("user_id", java.util.UUID::class.java),
                    )
                }.firstOrNull()
                ?: raise(CommonDomainError("EMPLOYEE_NOT_FOUND", Messages.EmployeeNotFound.localize()))

        val (email, name, userId) = row
        if (email == null) {
            raise(CommonDomainError("EMPLOYEE_NO_EMAIL", Messages.EmployeeNotFound.localize()))
        }

        val newHash = passwordHasher.hash(request.password)

        db.transaction {
            sql("UPDATE users SET password_hash = :hash WHERE id = :userId")
                .bind("hash", newHash.value)
                .bind("userId", userId!!)
                .execute()

            sql("UPDATE employees SET is_active = true WHERE id = :employeeId")
                .bind("employeeId", request.employeeId.toJavaUuid())
                .execute()
        }

        val textBody =
            """
            Здравствуйте, $name!

            Администратор предоставил вам доступ в AthleticaCRM.

            Логин: $email
            Пароль: ${request.password}

            После входа вы можете сменить пароль в настройках профиля.
            """.trimIndent()

        val htmlBody =
            """
            <p>Здравствуйте, <b>$name</b>!</p>
            <p>Администратор предоставил вам доступ в AthleticaCRM.</p>
            <p><b>Логин:</b> $email<br>
            <b>Пароль:</b> ${request.password}</p>
            <p>После входа вы можете сменить пароль в настройках профиля.</p>
            """.trimIndent()

        mailbox.send(
            Email(
                subject = Subject("Доступ в AthleticaCRM"),
                text = EmailTextBody(textBody),
                html = EmailHtmlBody(htmlBody),
                to = listOf(EmailAddress(email)),
            ),
        )

        audit.logCreate("employee_access_sent", request.employeeId, "{\"employeeId\":\"${request.employeeId}\"}")
    }
