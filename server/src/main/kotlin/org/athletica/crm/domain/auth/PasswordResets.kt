package org.athletica.crm.domain.auth

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.Lang
import org.athletica.crm.core.SystemContexts
import org.athletica.crm.core.entityids.toOrgId
import org.athletica.crm.core.entityids.toUserId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.domain.audit.AuditActionType
import org.athletica.crm.domain.audit.AuditEvent
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.mail.OrgEmails
import org.athletica.crm.domain.mail.passwordResetEmail
import org.athletica.crm.i18n.Messages
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * Восстановление пароля по ссылке из письма. Ссылка одна на пользователя: новая заменяет прежнюю,
 * действует час и срабатывает один раз. Сброс завершает все сессии пользователя.
 */
class PasswordResets(
    private val emails: OrgEmails,
    private val sessions: AuthSessions,
    private val emailVerifications: EmailVerifications,
    private val audit: AuditLog,
    private val passwordHasher: PasswordHasher,
    private val config: EmailVerificationConfig,
    private val clock: Clock = Clock.System,
) {
    /**
     * Ставит в очередь письмо со ссылкой сброса на [email] на языке [lang], если адрес принадлежит
     * незаблокированному сотруднику и с прошлого письма прошло не меньше минуты. Иначе ничего не делает:
     * вызывающий не узнаёт, было ли письмо, чтобы ответ не раскрывал зарегистрированные адреса.
     */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun request(email: String, lang: Lang) {
        val token = newOneTimeToken()
        val now = clock.now()
        val recipient =
            tr.sql(
                """
                WITH upserted AS (
                    INSERT INTO password_resets (user_id, token_hash, expires_at, last_sent_at)
                    SELECT u.id, :hash, :expiresAt, :now
                    FROM users u JOIN employees e ON e.user_id = u.id
                    WHERE u.login = :email AND e.blocked_at IS NULL
                    ON CONFLICT (user_id) DO UPDATE
                        SET token_hash = EXCLUDED.token_hash, expires_at = EXCLUDED.expires_at,
                            last_sent_at = EXCLUDED.last_sent_at, used_at = NULL
                        WHERE password_resets.last_sent_at <= :pauseEnd
                    RETURNING user_id
                )
                SELECT u.login, e.org_id, e.name
                FROM upserted p JOIN users u ON u.id = p.user_id JOIN employees e ON e.user_id = p.user_id
                """.trimIndent(),
            )
                .bind("hash", oneTimeTokenHash(token))
                .bind("expiresAt", now + 1.hours)
                .bind("now", now)
                .bind("email", email)
                .bind("pauseEnd", now - 60.seconds)
                .firstOrNull { Triple(it.asString("login"), it.asUuid("org_id").toOrgId(), it.asString("name")) }
                ?: return
        val (login, orgId, name) = recipient
        val message = passwordResetEmail(EmailAddress(login), name, lang, config.passwordResetLink(token))
        context(SystemContexts.forOrg(orgId)) {
            emails.schedule(message.to, message.subject.value, message.text.value, message.html.value)
        }
    }

    /**
     * Устанавливает пароль [newPassword] по токену [token] из письма: расходует ссылку, завершает все
     * сессии пользователя, подтверждает ожидающий email и пишет событие audit с IP [clientIp].
     * Неизвестная, просроченная, использованная ссылка или ссылка заблокированного сотрудника —
     * ошибка `INVALID_PASSWORD_RESET` на языке [lang].
     */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun reset(
        token: String,
        newPassword: String,
        lang: Lang,
        clientIp: String?,
    ) {
        if (!token.isOneTimeTokenFormat()) {
            invalid(lang)
        }
        if (newPassword.isEmpty()) {
            raise(CommonDomainError("EMPTY_PASSWORD", Messages.EmptyNewPassword.localize(lang)))
        }
        val now = clock.now()
        val owner =
            tr.sql(
                """
                UPDATE password_resets r SET used_at = :now
                FROM employees e
                WHERE r.token_hash = :hash AND r.used_at IS NULL AND r.expires_at > :now
                    AND e.user_id = r.user_id AND e.blocked_at IS NULL
                RETURNING r.user_id, e.org_id, e.name
                """.trimIndent(),
            )
                .bind("now", now)
                .bind("hash", oneTimeTokenHash(token))
                .firstOrNull { Triple(it.asUuid("user_id").toUserId(), it.asUuid("org_id").toOrgId(), it.asString("name")) }
                ?: invalid(lang)
        val (userId, orgId, name) = owner
        tr.sql("UPDATE users SET password_hash = :hash WHERE id = :id")
            .bind("hash", passwordHasher.hash(newPassword).value)
            .bind("id", userId)
            .execute()
        sessions.deleteOthers(userId, null)
        emailVerifications.confirmOwnership(userId, clientIp)
        audit.log(
            AuditEvent(
                orgId = orgId,
                userId = userId,
                username = name,
                actionType = AuditActionType.AUTH_PASSWORD_RESET,
                ipAddress = clientIp,
            ),
        )
    }

    /** Поднимает единую ошибку недействительной ссылки сброса на языке [lang]. */
    context(raise: Raise<AppError>)
    private fun invalid(lang: Lang): Nothing = raise(CommonDomainError("INVALID_PASSWORD_RESET", Messages.InvalidPasswordReset.localize(lang)))
}
