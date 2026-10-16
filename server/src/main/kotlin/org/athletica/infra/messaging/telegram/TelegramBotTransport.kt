package org.athletica.infra.messaging.telegram

import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.domain.channels.ChannelTransport
import org.athletica.crm.domain.channels.SendResult

/** Транспорт «Telegram-бот» с настройками [settings]: отправляет текст через Bot API [api]. */
class TelegramBotTransport(
    private val settings: ChannelConfig.TelegramBot,
    private val api: TelegramBotApi,
) : ChannelTransport {
    override suspend fun send(address: String, body: String): SendResult =
        when (val result = api.sendMessage(settings.botToken, address, body)) {
            is TelegramResult.Ok -> SendResult.Sent(result.value.messageId.toString())
            TelegramResult.Unavailable -> SendResult.TransientFailure("TELEGRAM_UNAVAILABLE", "Bot API недоступен ни через один прокси")
            is TelegramResult.Error -> result.toSendResult()
        }

    /** Ошибка Bot API в терминах диспетчера: временная — повторить, окончательная — не доставлено. */
    private fun TelegramResult.Error.toSendResult(): SendResult =
        when {
            status == 429 || status >= 500 -> SendResult.TransientFailure("TELEGRAM_$status", description)
            status == 403 -> SendResult.PermanentFailure("BOT_BLOCKED", description)
            status == 400 && description.contains("chat not found", ignoreCase = true) ->
                SendResult.PermanentFailure("CHAT_NOT_FOUND", description)
            else -> SendResult.PermanentFailure("TELEGRAM_$status", description)
        }
}
