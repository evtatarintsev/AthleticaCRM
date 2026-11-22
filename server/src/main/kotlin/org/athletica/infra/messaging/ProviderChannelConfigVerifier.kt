package org.athletica.infra.messaging

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.domain.channels.ChannelConfigVerifier
import org.athletica.crm.i18n.Messages
import org.athletica.infra.messaging.smsaero.SmsAeroApi
import org.athletica.infra.messaging.smsaero.SmsAeroCredentials
import org.athletica.infra.messaging.smsaero.SmsAeroResult
import org.athletica.infra.messaging.telegram.TelegramBotApi
import org.athletica.infra.messaging.telegram.TelegramResult

/**
 * Проверка настроек каналов у провайдеров: Telegram-бот проверяется через `getMe` Bot API [telegram]
 * и получает имя бота; SMS Aero — учётные данные и подпись через API [smsAero]; настройки
 * SMSC и Twilio возвращаются как есть.
 */
class ProviderChannelConfigVerifier(
    private val telegram: TelegramBotApi,
    private val smsAero: SmsAeroApi,
) : ChannelConfigVerifier {
    context(ctx: RequestContext, raise: Raise<AppError>)
    override suspend fun verified(config: ChannelConfig): ChannelConfig =
        when (config) {
            is ChannelConfig.TelegramBot -> verifiedTelegram(config)
            is ChannelConfig.SmsAeroSms -> verifiedSmsAero(config)
            is ChannelConfig.SmscSms, is ChannelConfig.TwilioSms -> config
        }

    /**
     * Конфиг SMS Aero с обрезанными пробелами, если SMS Aero принял email и ключ, а подпись есть в
     * аккаунте и одобрена. Ошибки: `SMS_AERO_FIELDS_REQUIRED` — пустое поле; `SMS_AERO_AUTH_INVALID` —
     * учётные данные отклонены; `SMS_AERO_SIGN_NOT_FOUND` / `SMS_AERO_SIGN_NOT_APPROVED` — подписи нет или
     * она не одобрена; `SMS_AERO_UNAVAILABLE` — SMS Aero не ответил или ответил иной ошибкой.
     */
    context(ctx: RequestContext, raise: Raise<AppError>)
    private suspend fun verifiedSmsAero(config: ChannelConfig.SmsAeroSms): ChannelConfig.SmsAeroSms {
        val trimmed = ChannelConfig.SmsAeroSms(email = config.email.trim(), apiKey = config.apiKey.trim(), sign = config.sign.trim())
        if (trimmed.email.isEmpty() || trimmed.apiKey.isEmpty() || trimmed.sign.isEmpty()) {
            raise(CommonDomainError("SMS_AERO_FIELDS_REQUIRED", Messages.SmsAeroFieldsRequired.localize()))
        }
        val credentials = SmsAeroCredentials(trimmed.email, trimmed.apiKey)
        when (val auth = smsAero.auth(credentials)) {
            is SmsAeroResult.Ok -> Unit
            is SmsAeroResult.Error ->
                if (auth.status == 401 || auth.status == 403) {
                    raise(CommonDomainError("SMS_AERO_AUTH_INVALID", Messages.SmsAeroAuthInvalid.localize()))
                } else {
                    raise(smsAeroUnavailable())
                }
            SmsAeroResult.Unavailable, SmsAeroResult.NoResponse -> raise(smsAeroUnavailable())
        }
        val sign =
            when (val result = smsAero.sign(credentials, trimmed.sign)) {
                is SmsAeroResult.Ok -> result.value
                is SmsAeroResult.Error, SmsAeroResult.Unavailable, SmsAeroResult.NoResponse -> raise(smsAeroUnavailable())
            }
        if (sign == null) {
            raise(CommonDomainError("SMS_AERO_SIGN_NOT_FOUND", Messages.SmsAeroSignNotFound.localize(trimmed.sign)))
        }
        if (!sign.approved) {
            raise(CommonDomainError("SMS_AERO_SIGN_NOT_APPROVED", Messages.SmsAeroSignNotApproved.localize(trimmed.sign)))
        }
        return trimmed
    }

    /** Ошибка «SMS Aero недоступен». */
    context(ctx: RequestContext)
    private fun smsAeroUnavailable(): CommonDomainError = CommonDomainError("SMS_AERO_UNAVAILABLE", Messages.SmsAeroUnavailable.localize())

    /**
     * Токен бота действителен — конфиг с именем бота из `getMe`. `TELEGRAM_TOKEN_INVALID` — Bot API
     * отклонил токен; `TELEGRAM_UNAVAILABLE` — Bot API недоступен ни через один прокси.
     */
    context(ctx: RequestContext, raise: Raise<AppError>)
    private suspend fun verifiedTelegram(config: ChannelConfig.TelegramBot): ChannelConfig.TelegramBot {
        val token = config.botToken.trim()
        if (token.isEmpty() || token.contains('/')) {
            raise(tokenInvalid())
        }
        return when (val me = telegram.getMe(token)) {
            is TelegramResult.Ok -> config.copy(botToken = token, botUsername = me.value.username.orEmpty())
            is TelegramResult.Error ->
                if (me.status == 401 || me.status == 404) {
                    raise(tokenInvalid())
                } else {
                    raise(CommonDomainError("TELEGRAM_UNAVAILABLE", Messages.TelegramUnavailable.localize()))
                }
            TelegramResult.Unavailable -> raise(CommonDomainError("TELEGRAM_UNAVAILABLE", Messages.TelegramUnavailable.localize()))
        }
    }

    /** Ошибка «токен бота недействителен». */
    context(ctx: RequestContext)
    private fun tokenInvalid(): CommonDomainError = CommonDomainError("TELEGRAM_TOKEN_INVALID", Messages.TelegramTokenInvalid.localize())
}
