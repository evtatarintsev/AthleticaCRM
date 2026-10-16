package org.athletica.infra.messaging

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.domain.channels.ChannelConfigVerifier
import org.athletica.crm.i18n.Messages
import org.athletica.infra.messaging.telegram.TelegramBotApi
import org.athletica.infra.messaging.telegram.TelegramResult

/**
 * Проверка настроек каналов у провайдеров: Telegram-бот проверяется через `getMe` Bot API [telegram]
 * и получает имя бота; настройки SMS-провайдеров возвращаются как есть.
 */
class ProviderChannelConfigVerifier(
    private val telegram: TelegramBotApi,
) : ChannelConfigVerifier {
    context(ctx: RequestContext, raise: Raise<DomainError>)
    override suspend fun verified(config: ChannelConfig): ChannelConfig =
        when (config) {
            is ChannelConfig.TelegramBot -> verifiedTelegram(config)
            is ChannelConfig.SmscSms, is ChannelConfig.TwilioSms -> config
        }

    /**
     * Токен бота действителен — конфиг с именем бота из `getMe`. `TELEGRAM_TOKEN_INVALID` — Bot API
     * отклонил токен; `TELEGRAM_UNAVAILABLE` — Bot API недоступен ни через один прокси.
     */
    context(ctx: RequestContext, raise: Raise<DomainError>)
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
