package org.athletica.crm.domain.auth

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.AuthSessionId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.SessionRevoked
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Реализация [AuthSessions] поверх таблицы `auth_sessions`. */
class DbAuthSessions : AuthSessions {
    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun new(
        userId: UserId,
        orgId: OrgId,
        userAgent: String?,
        ip: String?,
        now: Instant,
        staleBefore: Instant,
    ): IssuedSession {
        tr.sql("DELETE FROM auth_sessions WHERE user_id = :userId AND last_seen_at < :staleBefore")
            .bind("userId", userId)
            .bind("staleBefore", staleBefore)
            .execute()
        val session = IssuedSession(AuthSessionId.new(), Uuid.generateV7())
        tr.sql(
            """
            INSERT INTO auth_sessions (id, user_id, org_id, created_at, last_seen_at, user_agent, ip, refresh_jti, rotated_at)
            VALUES (:id, :userId, :orgId, :now, :now, :userAgent, :ip, :jti, :now)
            """.trimIndent(),
        )
            .bind("id", session.id)
            .bind("userId", userId)
            .bind("orgId", orgId)
            .bind("now", now)
            .bind("userAgent", userAgent?.take(MAX_USER_AGENT_LENGTH))
            .bind("ip", ip)
            .bind("jti", session.refreshJti)
            .execute()
        return session
    }

    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun rotate(
        id: AuthSessionId,
        presentedJti: Uuid,
        ip: String?,
        now: Instant,
    ): IssuedSession? {
        val current =
            tr.sql("SELECT refresh_jti, prev_refresh_jti, rotated_at FROM auth_sessions WHERE id = :id FOR UPDATE")
                .bind("id", id)
                .firstOrNull { row -> Triple(row.asUuid("refresh_jti"), row.asUuidOrNull("prev_refresh_jti"), row.asInstant("rotated_at")) }
                ?: return null
        val (jti, prevJti, rotatedAt) = current
        val accepted = presentedJti == jti || (presentedJti == prevJti && now - rotatedAt <= ROTATION_GRACE)
        if (!accepted) {
            tr.sql("DELETE FROM auth_sessions WHERE id = :id").bind("id", id).execute()
            return null
        }
        return replaceJti(id, now, ip)
    }

    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun reissue(id: AuthSessionId, now: Instant): IssuedSession = replaceJti(id, now, null) ?: raise(SessionRevoked)

    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun requireActive(id: AuthSessionId, userId: UserId) {
        val exists =
            tr.sql("SELECT 1 FROM auth_sessions WHERE id = :id AND user_id = :userId")
                .bind("id", id)
                .bind("userId", userId)
                .firstOrNull { true } ?: false
        ensure(exists) { SessionRevoked }
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun delete(id: AuthSessionId, userId: UserId) {
        val deleted =
            tr.sql("DELETE FROM auth_sessions WHERE id = :id AND user_id = :userId")
                .bind("id", id)
                .bind("userId", userId)
                .execute()
        ensure(deleted == 1L) { CommonDomainError("AUTH_SESSION_NOT_FOUND", Messages.AuthSessionNotFound.localize()) }
    }

    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun deleteOthers(userId: UserId, keep: AuthSessionId?) {
        if (keep == null) {
            tr.sql("DELETE FROM auth_sessions WHERE user_id = :userId").bind("userId", userId).execute()
        } else {
            tr.sql("DELETE FROM auth_sessions WHERE user_id = :userId AND id <> :keep")
                .bind("userId", userId)
                .bind("keep", keep)
                .execute()
        }
    }

    /**
     * Выпускает новый токен обновления сессии [id] в момент [now]: прежний становится предыдущим,
     * IP обновляется на [ip], если он известен. Возвращает `null`, если сессии нет.
     */
    context(tr: Transaction, raise: Raise<AppError>)
    private suspend fun replaceJti(id: AuthSessionId, now: Instant, ip: String?): IssuedSession? {
        val session = IssuedSession(id, Uuid.generateV7())
        val updated =
            tr.sql(
                """
                UPDATE auth_sessions
                SET prev_refresh_jti = refresh_jti,
                    refresh_jti      = :jti,
                    rotated_at       = :now,
                    last_seen_at     = :now,
                    ip               = COALESCE(CAST(:ip AS TEXT), ip)
                WHERE id = :id
                """.trimIndent(),
            )
                .bind("id", id)
                .bind("jti", session.refreshJti)
                .bind("now", now)
                .bind("ip", ip)
                .execute()
        return session.takeIf { updated == 1L }
    }

    private companion object {
        /** Сколько после замены ещё принимается предыдущий токен обновления: гонка вкладок и повтор после обрыва связи. */
        val ROTATION_GRACE = 60.seconds

        /** Предел длины сохраняемого User-Agent. */
        const val MAX_USER_AGENT_LENGTH = 512
    }
}
