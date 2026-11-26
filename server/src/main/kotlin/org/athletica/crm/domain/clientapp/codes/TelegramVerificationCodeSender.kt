package org.athletica.crm.domain.clientapp.codes

import arrow.core.getOrElse
import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import arrow.core.raise.context.raise
import org.athletica.crm.core.SystemContexts
import org.athletica.crm.core.SystemRequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.domain.clientapp.auth.CODE_TTL
import org.athletica.crm.domain.clientcontacts.PhoneDigits
import org.athletica.crm.domain.telegramgateway.GatewayCode
import org.athletica.crm.domain.telegramgateway.GatewayCodeResult
import org.athletica.crm.domain.telegramgateway.GatewayCodeTtl
import org.athletica.crm.domain.telegramgateway.TelegramGateway
import org.athletica.crm.storage.Database

/**
 * Отправитель кода в Telegram через Telegram Gateway организации-плательщика ([CodePayers.telegram]).
 * Нет плательщика или на номер не доставить — [CodeDeliveryError.Undeliverable], и политика доставки уходит в SMS.
 */
class TelegramVerificationCodeSender(
    private val db: Database,
    private val gateway: TelegramGateway,
) : VerificationCodeSender {
    override val channel = CodeChannel.TELEGRAM

    context(raise: Raise<CodeDeliveryError>)
    override suspend fun send(
        phone: PhoneDigits,
        code: VerificationCode,
    ) {
        val ctx =
            either<AppError, SystemRequestContext?> {
                db.transaction { CodePayers.telegram(phone)?.let { SystemContexts.forOrg(it) } }
            }.getOrElse { raise(CodeDeliveryError.Failed(it.message)) }
                ?: raise(CodeDeliveryError.Undeliverable)
        val result =
            either<AppError, GatewayCodeResult> {
                context(ctx) {
                    this@TelegramVerificationCodeSender.gateway.send(phone, GatewayCode.from(code.value), GatewayCodeTtl.from(CODE_TTL.inWholeSeconds.toInt()))
                }
            }.getOrNull() ?: raise(CodeDeliveryError.Failed("TELEGRAM_GATEWAY_ERROR"))
        when (result) {
            is GatewayCodeResult.Sent -> Unit
            GatewayCodeResult.NotConfigured, GatewayCodeResult.Undeliverable -> raise(CodeDeliveryError.Undeliverable)
            is GatewayCodeResult.Failed -> raise(CodeDeliveryError.Failed(result.reason))
        }
    }
}
