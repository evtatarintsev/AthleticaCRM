package org.athletica.crm.domain.clientapp.auth

import arrow.core.raise.context.Raise
import org.athletica.crm.core.entityids.ClientAppAccountId
import org.athletica.crm.core.entityids.ClientAppSessionId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Реализация [ClientAppSessions] поверх таблицы `client_app_sessions`. */
class DbClientAppSessions : ClientAppSessions {
    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun new(
        accountId: ClientAppAccountId,
        name: String?,
        now: Instant,
        staleBefore: Instant,
    ): IssuedClientAppSession {
        tr.sql("DELETE FROM client_app_sessions WHERE account_id = :accountId AND last_seen_at < :staleBefore")
            .bind("accountId", accountId)
            .bind("staleBefore", staleBefore)
            .execute()
        val session = IssuedClientAppSession(ClientAppSessionId.new(), Uuid.generateV7())
        tr.sql(
            """
            INSERT INTO client_app_sessions (id, account_id, platform, name, created_at, last_seen_at, refresh_jti, rotated_at)
            VALUES (:id, :accountId, :platform, :name, :now, :now, :jti, :now)
            """.trimIndent(),
        )
            .bind("id", session.id)
            .bind("accountId", accountId)
            .bind("platform", PLATFORM_WEB)
            .bind("name", name?.take(MAX_NAME_LENGTH))
            .bind("now", now)
            .bind("jti", session.refreshJti)
            .execute()
        return session
    }

    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun rotate(
        id: ClientAppSessionId,
        accountId: ClientAppAccountId,
        presentedJti: Uuid,
        now: Instant,
    ): IssuedClientAppSession? {
        val (jti, prevJti, rotatedAt) =
            tr.sql(
                """
                SELECT s.refresh_jti, s.prev_refresh_jti, s.rotated_at
                FROM client_app_sessions s
                JOIN client_app_accounts acc ON acc.id = s.account_id AND acc.deleted_at IS NULL
                WHERE s.id = :id AND s.account_id = :accountId
                FOR UPDATE OF s
                """.trimIndent(),
            )
                .bind("id", id)
                .bind("accountId", accountId)
                .firstOrNull { row -> Triple(row.asUuid("refresh_jti"), row.asUuidOrNull("prev_refresh_jti"), row.asInstant("rotated_at")) }
                ?: return null
        val accepted = presentedJti == jti || (presentedJti == prevJti && now - rotatedAt <= ROTATION_GRACE)
        if (!accepted) {
            tr.sql("DELETE FROM client_app_sessions WHERE id = :id").bind("id", id).execute()
            return null
        }
        if (presentedJti == prevJti) {
            return IssuedClientAppSession(id, jti)
        }
        val session = IssuedClientAppSession(id, Uuid.generateV7())
        tr.sql(
            """
            UPDATE client_app_sessions
            SET prev_refresh_jti = refresh_jti, refresh_jti = :jti, rotated_at = :now, last_seen_at = :now
            WHERE id = :id
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("jti", session.refreshJti)
            .bind("now", now)
            .execute()
        return session
    }

    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun isActive(id: ClientAppSessionId, accountId: ClientAppAccountId): Boolean =
        tr.sql(
            """
            SELECT 1 FROM client_app_sessions s
            JOIN client_app_accounts acc ON acc.id = s.account_id AND acc.deleted_at IS NULL
            WHERE s.id = :id AND s.account_id = :accountId
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("accountId", accountId)
            .firstOrNull { true } ?: false

    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun delete(id: ClientAppSessionId, accountId: ClientAppAccountId) {
        tr.sql("DELETE FROM client_app_sessions WHERE id = :id AND account_id = :accountId")
            .bind("id", id)
            .bind("accountId", accountId)
            .execute()
    }

    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun deleteAll(accountId: ClientAppAccountId) {
        tr.sql("DELETE FROM client_app_sessions WHERE account_id = :accountId").bind("accountId", accountId).execute()
    }

    private companion object {
        /** Сколько после замены ещё принимается предыдущий токен обновления: гонка вкладок и повтор после обрыва связи. */
        val ROTATION_GRACE = 60.seconds

        /** Платформа сеанса веб-кабинета; мобильные приложения добавят свои. */
        const val PLATFORM_WEB = "WEB"

        /** Предел длины названия сеанса. */
        const val MAX_NAME_LENGTH = 128
    }
}
