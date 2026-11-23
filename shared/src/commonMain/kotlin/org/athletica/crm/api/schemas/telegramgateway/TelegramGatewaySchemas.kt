package org.athletica.crm.api.schemas.telegramgateway

import kotlinx.serialization.Serializable
import org.athletica.crm.core.money.Money
import kotlin.time.Instant

/** Настройки Telegram Gateway организации; [integration] `null` — Gateway не подключали. */
@Serializable
data class TelegramGatewaySettingsResponse(
    val integration: TelegramGatewayIntegrationSchema?,
)

/** Подключённая интеграция с Telegram Gateway. Значение токена не передаётся. */
@Serializable
data class TelegramGatewayIntegrationSchema(
    /** Маска токена: `••••` и последние 4 символа, у токена короче 12 символов — только `••••`. */
    val tokenMask: String,
    /** Уходят ли коды через интеграцию. */
    val enabled: Boolean,
    /** Остаток по последнему платному ответу Gateway; `null` — такого ответа ещё не было. */
    val balance: TelegramGatewayBalanceSchema?,
)

/** Остаток баланса Gateway [amount] (в долларах) на момент ответа [at]. */
@Serializable
data class TelegramGatewayBalanceSchema(
    val amount: Money,
    val at: Instant,
)

/** Подключение Gateway или замена токена [token]; токен проверяется у Telegram до сохранения. */
@Serializable
data class SaveTelegramGatewayTokenRequest(
    val token: String,
)

/** Включение или выключение интеграции. */
@Serializable
data class SetTelegramGatewayEnabledRequest(
    val enabled: Boolean,
)

/** Отправка тестового кода на номер [phone]. */
@Serializable
data class SendTelegramGatewayTestCodeRequest(
    val phone: String,
)

/** Исход отправки кода через Telegram Gateway. */
@Serializable
enum class TelegramGatewayCodeOutcome {
    /** Код отправлен. */
    SENT,

    /** На номер Telegram доставить не может. */
    UNDELIVERABLE,

    /** Интеграция не подключена или выключена. */
    NOT_CONFIGURED,

    /** Gateway ответил ошибкой или не ответил. */
    FAILED,
}

/** Исход тестовой отправки; [reason] — причина для [TelegramGatewayCodeOutcome.FAILED]. Код не возвращается. */
@Serializable
data class TelegramGatewayTestCodeResponse(
    val outcome: TelegramGatewayCodeOutcome,
    val reason: String?,
)
