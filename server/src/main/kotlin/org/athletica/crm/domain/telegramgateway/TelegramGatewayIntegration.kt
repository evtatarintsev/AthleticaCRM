package org.athletica.crm.domain.telegramgateway

import org.athletica.crm.core.money.Money
import kotlin.time.Instant

/** Подключённый организацией аккаунт Telegram Gateway. */
data class TelegramGatewayIntegration(
    /** Токен аккаунта Gateway; наружу отдаётся только маской. */
    val token: String,
    /** Уходят ли коды через интеграцию. */
    val enabled: Boolean,
    /** Остаток по последнему платному ответу Gateway; `null` — такого ответа ещё не было. */
    val balance: GatewayBalance?,
) {
    /** Без токена: строка может попасть в журнал. */
    override fun toString(): String = "TelegramGatewayIntegration(enabled=$enabled, balance=$balance)"
}

/** Остаток баланса Gateway [amount] (в долларах, валюте поставщика) на момент ответа [at]. */
data class GatewayBalance(
    /** Остаток в [org.athletica.crm.core.money.Currency.USD]. */
    val amount: Money,
    /** Время ответа Gateway, в котором пришёл остаток. */
    val at: Instant,
)
