package org.athletica.crm.usecases.employees

import arrow.core.Either
import arrow.core.raise.either
import org.athletica.crm.api.schemas.employees.SendEmployeeAccessRequest
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import org.athletica.crm.db.asString
import org.athletica.crm.db.asStringOrNull
import org.athletica.crm.db.asUuid
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logCreate
import org.athletica.crm.i18n.Messages
import org.athletica.crm.security.PasswordHasher
import org.athletica.infra.mail.Email
import org.athletica.infra.mail.EmailAddress
import org.athletica.infra.mail.EmailHtmlBody
import org.athletica.infra.mail.EmailTextBody
import org.athletica.infra.mail.Mailbox
import org.athletica.infra.mail.Subject

/**
 * Отправляет доступ сотруднику [request.employeeId]:
 * устанавливает новый пароль, активирует аккаунт (`is_active = true`) и высылает письмо с логином и паролем.
 *
 * Обновление пароля и активация выполняются атомарно в одной транзакции.
 * После успешной записи в БД отправляется email через [mailbox].
 * Событие фиксируется в журнале аудита как `employee_access_sent`.
 *
 * Сотрудник должен принадлежать организации из контекста [ctx].
 *
 * Возможные ошибки:
 * - `EMPLOYEE_NOT_FOUND` — сотрудник не найден в организации или не имеет email.
 */
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
                ).bind("employeeId", request.employeeId)
                .bind("orgId", ctx.orgId)
                .list { row, _ ->
                    Triple(
                        row.asStringOrNull("email"),
                        row.asString("name"),
                        row.asUuid("user_id"),
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
                .bind("userId", userId)
                .execute()

            sql("UPDATE employees SET is_active = true WHERE id = :employeeId")
                .bind("employeeId", request.employeeId)
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
