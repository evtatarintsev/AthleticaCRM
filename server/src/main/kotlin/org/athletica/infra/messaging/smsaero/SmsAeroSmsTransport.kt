package org.athletica.infra.messaging.smsaero

import org.athletica.crm.core.Lang
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.domain.channels.ChannelTransport
import org.athletica.crm.domain.channels.SendResult
import org.athletica.crm.domain.clientcontacts.PhoneDigits
import org.athletica.crm.i18n.Messages
import org.slf4j.LoggerFactory

/**
 * Транспорт «SMS через SMS Aero» с настройками [settings]: отправляет текст через API [api] с
 * аккаунта и от подписи канала. Отказ SMS Aero — окончательный, недоступность — временная;
 * таймаут без ответа — окончательный: SMS могло уйти, а повтор отправил бы платный дубль.
 * В журнал пишутся код, причина от SMS Aero и последние 4 цифры номера; ключ и текст — нет.
 */
class SmsAeroSmsTransport(
    private val settings: ChannelConfig.SmsAeroSms,
    private val api: SmsAeroApi,
) : ChannelTransport {
    private val logger = LoggerFactory.getLogger(SmsAeroSmsTransport::class.java)

    override suspend fun send(address: String, body: String): SendResult {
        val number = PhoneDigits.from(address)?.value
        val result =
            if (number == null) {
                SendResult.PermanentFailure("SMS_AERO_REJECTED", Messages.SmsAeroRejected.localize(LANG, "invalid number"))
            } else {
                when (val sent = api.send(SmsAeroCredentials(settings.email, settings.apiKey), number, body, settings.sign)) {
                    is SmsAeroResult.Ok -> SendResult.Sent(sent.value)
                    is SmsAeroResult.Error -> sent.toSendResult()
                    SmsAeroResult.Unavailable -> SendResult.TransientFailure("SMS_AERO_UNAVAILABLE", Messages.SmsAeroUnavailable.localize(LANG))
                    SmsAeroResult.NoResponse -> SendResult.PermanentFailure("SMS_AERO_NO_RESPONSE", Messages.SmsAeroNoResponse.localize(LANG))
                }
            }
        if (result !is SendResult.Sent) {
            logger.warn("SMS Aero [{}] -> …{}: {}", settings.email, (number ?: address).takeLast(4), result)
        }
        return result
    }

    /** Отказ SMS Aero в терминах диспетчера: все отказы окончательные, повтор не поможет. */
    private fun SmsAeroResult.Error.toSendResult(): SendResult =
        when (status) {
            402 -> SendResult.PermanentFailure("SMS_AERO_NO_FUNDS", Messages.SmsAeroNoFunds.localize(LANG))
            404 -> SendResult.PermanentFailure("SMS_AERO_IP_FORBIDDEN", Messages.SmsAeroIpForbidden.localize(LANG))
            401, 403 -> SendResult.PermanentFailure("SMS_AERO_AUTH_INVALID", Messages.SmsAeroAuthInvalid.localize(LANG))
            else -> SendResult.PermanentFailure("SMS_AERO_REJECTED", Messages.SmsAeroRejected.localize(LANG, message))
        }

    private companion object {
        // shortcut: транспорт вызывается вне запроса и не знает языка организации — причины по-русски; перейти на язык организации, когда он появится в настройках
        val LANG = Lang.RU
    }
}
