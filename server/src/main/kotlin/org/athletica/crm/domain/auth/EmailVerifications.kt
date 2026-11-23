package org.athletica.crm.domain.auth

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.Row
import kotlinx.serialization.json.Json
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.SystemContexts
import org.athletica.crm.core.audit.AuditActionType
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.entityids.toOrgId
import org.athletica.crm.core.entityids.toUserId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.domain.audit.AuditEvent
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.mail.OrgEmails
import org.athletica.crm.domain.mail.registrationEmail
import org.athletica.crm.domain.notifications.EmailConfirmed
import org.athletica.crm.domain.notifications.Notifications
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asInstantOrNull
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import kotlin.math.ceil
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class EmailVerifications(
    private val emails: OrgEmails,
    private val notifications: Notifications,
    private val audit: AuditLog,
    private val config: EmailVerificationConfig,
    private val clock: Clock = Clock.System,
) {
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun register(user: AuthenticatedUser, name: String, lang: Lang) {
        val token = newOneTimeToken()
        val now = clock.now()
        tr.sql(
            """
            INSERT INTO user_email_verifications (user_id, org_id, email, token_hash, expires_at, last_sent_at)
            VALUES (:userId, :orgId, :email, :hash, :expiresAt, :now)
            """.trimIndent(),
        )
            .bind("userId", user.id).bind("orgId", user.orgId).bind("email", user.username)
            .bind("hash", oneTimeTokenHash(token)).bind("expiresAt", now + 24.hours).bind("now", now).execute()
        schedule(user.orgId, user.username, name, lang, token)
    }

    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun pending(userId: UserId): Boolean =
        tr.sql(
            """
            SELECT EXISTS (SELECT 1 FROM user_email_verifications WHERE user_id = :userId AND verified_at IS NULL) AS pending
            """.trimIndent(),
        ).bind("userId", userId).firstOrNull { it.asBoolean("pending") } == true

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun resend(): ResendResult {
        val row =
            tr.sql("$select WHERE v.user_id = :userId FOR UPDATE OF v")
                .bind("userId", ctx.userId).firstOrNull { Verification(it) }
                ?: raise(CommonDomainError("EMAIL_VERIFICATION_UNAVAILABLE", Messages.EmailVerificationUnavailable.localize()))
        if (row.verifiedAt != null) {
            return ResendResult.AlreadyConfirmed
        }
        val now = clock.now()
        val remaining = ceil(60 - (now - row.lastSentAt).inWholeMilliseconds / 1000.0).toInt().coerceIn(0, 60)
        if (remaining > 0) {
            return ResendResult.RateLimited(remaining)
        }
        if (row.email != row.login) {
            invalid(ctx.lang)
        }
        val token = newOneTimeToken()
        tr.sql(
            """
            UPDATE user_email_verifications SET token_hash = :hash, expires_at = :expiresAt, last_sent_at = :now WHERE user_id = :userId
            """.trimIndent(),
        ).bind("hash", oneTimeTokenHash(token)).bind("expiresAt", now + 24.hours).bind("now", now).bind("userId", ctx.userId).execute()
        schedule(row.orgId, row.email, row.name, ctx.lang, token)
        return ResendResult.Queued(60)
    }

    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun confirm(token: String, lang: Lang, clientIp: String?): ConfirmResult {
        if (!token.isOneTimeTokenFormat()) {
            invalid(lang)
        }
        val row =
            tr.sql("$select WHERE v.token_hash = :hash FOR UPDATE OF v")
                .bind("hash", oneTimeTokenHash(token)).firstOrNull { Verification(it) } ?: invalid(lang)
        if (row.verifiedAt != null) {
            return ConfirmResult.ALREADY_CONFIRMED
        }
        if (clock.now() >= row.expiresAt || row.email != row.login) {
            invalid(lang)
        }
        markVerified(row, clientIp)
        return ConfirmResult.CONFIRMED
    }

    /**
     * Подтверждает email пользователя [userId], владение которым доказано иначе, чем ссылкой
     * подтверждения (сброс пароля по письму). Без ожидающего подтверждения или при сменившемся
     * логине ничего не делает. [clientIp] попадает в событие audit.
     */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun confirmOwnership(userId: UserId, clientIp: String?) {
        val row = tr.sql("$select WHERE v.user_id = :userId FOR UPDATE OF v").bind("userId", userId).firstOrNull { Verification(it) }
        if (row == null || row.verifiedAt != null || row.email != row.login) {
            return
        }
        markVerified(row, clientIp)
    }

    context(tr: Transaction, raise: Raise<AppError>)
    private suspend fun markVerified(row: Verification, clientIp: String?) {
        tr.sql("UPDATE user_email_verifications SET verified_at = :now WHERE user_id = :userId")
            .bind("now", clock.now()).bind("userId", row.userId).execute()
        context(SystemContexts.forOrg(row.orgId)) {
            notifications.new(EmailConfirmed(row.email), listOf(row.employeeId)).save()
        }
        audit.log(
            AuditEvent(
                orgId = row.orgId,
                userId = row.userId,
                username = row.name,
                actionType = AuditActionType.AUTH_VERIFY_EMAIL,
                data = Json.encodeToString(mapOf("email" to row.email)),
                ipAddress = clientIp,
            ),
        )
    }

    context(tr: Transaction, raise: Raise<AppError>)
    private suspend fun schedule(
        orgId: org.athletica.crm.core.entityids.OrgId,
        email: String,
        name: String,
        lang: Lang,
        token: String,
    ) {
        val message = registrationEmail(EmailAddress(email), name, lang, config.confirmationLink(token))
        context(SystemContexts.forOrg(orgId)) {
            emails.schedule(message.to, message.subject.value, message.text.value, message.html.value)
        }
    }

    context(raise: Raise<AppError>)
    private fun invalid(lang: Lang): Nothing = raise(CommonDomainError("INVALID_EMAIL_CONFIRMATION", Messages.InvalidEmailConfirmation.localize(lang)))

    private class Verification(row: Row) {
        val userId = row.asUuid("user_id").toUserId()
        val orgId = row.asUuid("org_id").toOrgId()
        val employeeId = row.asUuid("employee_id").toEmployeeId()
        val email = row.asString("email")
        val login = row.asString("login")
        val name = row.asString("name")
        val expiresAt = row.asInstant("expires_at")
        val lastSentAt = row.asInstant("last_sent_at")
        val verifiedAt = row.asInstantOrNull("verified_at")
    }

    private val select =
        """
        SELECT v.*, u.login, e.id AS employee_id, e.name
        FROM user_email_verifications v
        JOIN users u ON u.id = v.user_id
        JOIN employees e ON e.user_id = v.user_id AND e.org_id = v.org_id
        """.trimIndent()
}

/** Итог повторной отправки письма подтверждения email. */
sealed interface ResendResult {
    /** Письмо поставлено в очередь; следующую отправку можно запросить через [retryAfterSeconds] секунд. */
    data class Queued(val retryAfterSeconds: Int) : ResendResult

    /** Email уже подтверждён, письмо не отправлялось. */
    data object AlreadyConfirmed : ResendResult

    /** Предыдущее письмо отправлено недавно; повторить можно через [retryAfterSeconds] секунд. */
    data class RateLimited(val retryAfterSeconds: Int) : ResendResult
}

/** Итог подтверждения email по ссылке. */
enum class ConfirmResult {
    /** Email подтверждён этим запросом. */
    CONFIRMED,

    /** Email был подтверждён ранее. */
    ALREADY_CONFIRMED,
}
