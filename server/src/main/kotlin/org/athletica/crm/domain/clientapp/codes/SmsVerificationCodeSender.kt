package org.athletica.crm.domain.clientapp.codes

import arrow.core.getOrElse
import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import arrow.core.raise.context.raise
import org.athletica.crm.core.Lang
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.domain.channels.ChannelTransports
import org.athletica.crm.domain.channels.SendResult
import org.athletica.crm.domain.clientcontacts.PhoneDigits
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Database

/**
 * Отправитель кода по SMS-каналу организации-плательщика ([CodePayers.sms]) — напрямую через транспорт,
 * мимо диспетчера сообщений и переписки клуба: повторять код бессмысленно. Нет плательщика —
 * [CodeDeliveryError.Undeliverable], отказ провайдера — [CodeDeliveryError.Failed].
 */
class SmsVerificationCodeSender(
    private val db: Database,
    private val transports: ChannelTransports,
) : VerificationCodeSender {
    override val channel = CodeChannel.SMS

    context(raise: Raise<CodeDeliveryError>)
    override suspend fun send(
        phone: PhoneDigits,
        code: VerificationCode,
    ) {
        val payer =
            either<AppError, SmsPayer?> {
                db.transaction { CodePayers.sms(phone) }
            }.getOrElse { raise(CodeDeliveryError.Failed(it.message)) }
                ?: raise(CodeDeliveryError.Undeliverable)
        val body = Messages.ClientAppSmsCode.localize(Lang.RU, payer.orgName, code.value)
        when (val result = transports.transport(payer.config).send("+${phone.value}", body)) {
            is SendResult.Sent -> Unit
            is SendResult.TransientFailure -> raise(CodeDeliveryError.Failed(result.code))
            is SendResult.PermanentFailure -> raise(CodeDeliveryError.Failed(result.code))
        }
    }
}
