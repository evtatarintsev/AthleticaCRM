package org.athletica.crm.domain.auth

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.AuthSessionId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Серверные сессии входа. Токены доступа и обновления принадлежат сессии и принимаются,
 * только пока она существует; завершение сессии — удаление её записи.
 */
interface AuthSessions {
    /**
     * Открывает новую сессию пользователя [userId] организации [orgId] с клиента [userAgent] и [ip]
     * в момент [now]. Заодно удаляет сессии этого пользователя без активности с момента [staleBefore].
     */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun new(
        userId: UserId,
        orgId: OrgId,
        userAgent: String?,
        ip: String?,
        now: Instant,
        staleBefore: Instant,
    ): IssuedSession

    /**
     * Меняет токен обновления сессии [id] по предъявленному [presentedJti] в момент [now],
     * запоминает IP [ip]. Возвращает `null`, если сессии нет или токен устарел вне окна гонки:
     * в последнем случае сессия удаляется, поэтому транзакцию вызывающего надо зафиксировать.
     */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun rotate(
        id: AuthSessionId,
        presentedJti: Uuid,
        ip: String?,
        now: Instant,
    ): IssuedSession?

    /** Выпускает новый токен обновления действующей сессии [id] без предъявления прежнего (смена филиала). */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun reissue(id: AuthSessionId, now: Instant): IssuedSession

    /** Проверяет, что сессия [id] пользователя [userId] существует; иначе поднимает [org.athletica.crm.core.errors.SessionRevoked]. */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun requireActive(id: AuthSessionId, userId: UserId)

    /** Завершает сессию [id] пользователя [userId]; чужая или несуществующая сессия — ошибка `AUTH_SESSION_NOT_FOUND`. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun delete(id: AuthSessionId, userId: UserId)

    /** Завершает все сессии пользователя [userId], кроме [keep]; при `null` — все. */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun deleteOthers(userId: UserId, keep: AuthSessionId?)
}

/** Сессия с действующим токеном обновления. */
data class IssuedSession(
    /** Идентификатор сессии — claim `sid` обоих токенов. */
    val id: AuthSessionId,
    /** Идентификатор действующего токена обновления — claim `jti`. */
    val refreshJti: Uuid,
)
