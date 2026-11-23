package org.athletica.crm.domain.telegramgateway

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.domain.clientcontacts.PhoneDigits

/**
 * Отправка одноразовых кодов через Telegram Gateway организации `ctx.orgId`.
 * Обращается к внешнему сервису, поэтому вызывается вне транзакции. Реализация — в `infra.telegram.gateway`.
 */
interface TelegramGateway {
    /**
     * Проверяет у Telegram токен [token] без списания и возвращает его без пробелов по краям.
     * `TELEGRAM_GATEWAY_TOKEN_INVALID` — токен отклонён, `TELEGRAM_GATEWAY_UNAVAILABLE` — Gateway не ответил.
     */
    context(ctx: RequestContext, raise: Raise<AppError>)
    suspend fun verifiedToken(token: String): String

    /**
     * Отправляет код [code] со сроком [ttl] на [phone] с токеном организации. Сначала проверяет,
     * можно ли доставить код, и платит за пару «проверка + отправка» один раз.
     * `Raise` — только сбой БД; исходы Gateway — [GatewayCodeResult].
     */
    context(ctx: RequestContext, raise: Raise<AppError>)
    suspend fun send(phone: PhoneDigits, code: GatewayCode, ttl: GatewayCodeTtl): GatewayCodeResult
}

/** Исход отправки кода через Telegram Gateway. */
sealed interface GatewayCodeResult {
    /** Код отправлен; [requestId] — идентификатор запроса Gateway. */
    data class Sent(val requestId: String) : GatewayCodeResult

    /** На номер доставить нельзя; с баланса ничего не списано. */
    data object Undeliverable : GatewayCodeResult

    /** У организации нет интеграции или она выключена; к Telegram не обращались. */
    data object NotConfigured : GatewayCodeResult

    /** Gateway ответил ошибкой или не ответил; [reason] — строка ошибки Gateway или `UNAVAILABLE`. */
    data class Failed(val reason: String) : GatewayCodeResult
}
