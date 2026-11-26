package org.athletica.crm.domain.clientapp.auth

import arrow.core.raise.context.Raise
import org.athletica.crm.core.entityids.ClientAppAccountId
import org.athletica.crm.core.entityids.ClientAppSessionId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Сеансы аккаунтов кабинета — браузеры и устройства. Правила те же, что у сеансов сотрудников
 * (`AuthSessions`): токены принимаются, пока существует запись сеанса; завершение — удаление записи.
 */
interface ClientAppSessions {
    /**
     * Открывает сеанс аккаунта [accountId] в браузере с названием [name] (`Safari, iOS`) в момент [now].
     * Заодно удаляет сеансы аккаунта без активности с момента [staleBefore].
     */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun new(
        accountId: ClientAppAccountId,
        name: String?,
        now: Instant,
        staleBefore: Instant,
    ): IssuedClientAppSession

    /**
     * Меняет токен обновления сеанса [id] аккаунта [accountId] по предъявленному [presentedJti] в момент [now].
     * Повтор с предыдущим токеном в окне гонки возвращает текущий без новой ротации.
     * `null` — сеанса нет или токен заменён раньше окна гонки; во втором случае сеанс удаляется,
     * поэтому транзакцию вызывающего надо зафиксировать.
     */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun rotate(
        id: ClientAppSessionId,
        accountId: ClientAppAccountId,
        presentedJti: Uuid,
        now: Instant,
    ): IssuedClientAppSession?

    /** Существует ли сеанс [id] аккаунта [accountId]. */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun isActive(id: ClientAppSessionId, accountId: ClientAppAccountId): Boolean

    /** Завершает сеанс [id] аккаунта [accountId]; отсутствующий сеанс — не ошибка. */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun delete(id: ClientAppSessionId, accountId: ClientAppAccountId)

    /** Завершает все сеансы аккаунта [accountId]. */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun deleteAll(accountId: ClientAppAccountId)
}

/** Сеанс с действующим токеном обновления. */
data class IssuedClientAppSession(
    /** Идентификатор сеанса — claim `sid` обоих токенов. */
    val id: ClientAppSessionId,
    /** Идентификатор действующего токена обновления — claim `jti`. */
    val refreshJti: Uuid,
)
