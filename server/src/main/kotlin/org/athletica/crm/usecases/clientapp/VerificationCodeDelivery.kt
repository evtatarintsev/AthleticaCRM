package org.athletica.crm.usecases.clientapp

import arrow.core.raise.context.either
import org.athletica.crm.core.clientapp.ClientAppAccessStatus
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.domain.clientapp.ClientAppAccesses
import org.athletica.crm.domain.clientapp.auth.ClientAppCodes
import org.athletica.crm.domain.clientapp.codes.CodeChannel
import org.athletica.crm.domain.clientapp.codes.CodeDeliveryError
import org.athletica.crm.domain.clientapp.codes.VerificationCode
import org.athletica.crm.domain.clientapp.codes.VerificationCodeSender
import org.athletica.crm.domain.clientcontacts.PhoneDigits
import org.athletica.crm.storage.Database
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid

/**
 * Политика доставки кода входа: запускается в фоне после ответа на запрос кода, поэтому время ответа
 * не зависит от номера и провайдера. Номер без доступа `приглашён`/`подключён` в организации
 * с включённым кабинетом кода не получает: платить некому. По умолчанию код уходит в Telegram,
 * а если туда не доставить — сразу по SMS; явный запрос SMS обходит Telegram. Канал, которым код
 * ушёл, записывается в строку кода; недоставленный код — только запись в лог.
 * [senders] — по одному отправителю на способ доставки.
 */
class VerificationCodeDelivery(
    private val db: Database,
    private val accesses: ClientAppAccesses,
    private val codes: ClientAppCodes,
    private val senders: List<VerificationCodeSender>,
) {
    private val logger = LoggerFactory.getLogger(VerificationCodeDelivery::class.java)

    /** Доставляет код [code] строки [codeId] на [phone]; [viaSms] — человек попросил SMS. */
    suspend fun deliver(
        phone: PhoneDigits,
        codeId: Uuid,
        code: VerificationCode,
        viaSms: Boolean,
    ) {
        either<AppError, Unit> {
            if (!payable(phone)) {
                return@either
            }
            val channels = if (viaSms) listOf(CodeChannel.SMS) else listOf(CodeChannel.TELEGRAM, CodeChannel.SMS)
            deliveredChannel(phone, code, channels)
                ?.let { db.transaction { codes.delivered(codeId, it) } }
                ?: logger.warn("Код входа в кабинет не доставлен на *{}", phone.value.takeLast(4))
        }.onLeft { logger.error("Доставка кода входа в кабинет: {} {}", it.code, it.message) }
    }

    /** Есть ли у номера [phone] организация, которая может заплатить за код. */
    private suspend fun payable(phone: PhoneDigits): Boolean =
        either<AppError, Boolean> {
            db.transaction { accesses.byPhone(phone).any { it.status in PAYABLE_STATUSES } }
        }.getOrNull() ?: false

    /**
     * Отправляет [code] на [phone] способами [channels] по очереди: следующий пробуется, только если
     * предыдущий ответил [CodeDeliveryError.Undeliverable]. Возвращает способ, которым код ушёл;
     * `null` — не ушёл никаким, окончательный отказ провайдера пишется в лог.
     */
    private suspend fun deliveredChannel(
        phone: PhoneDigits,
        code: VerificationCode,
        channels: List<CodeChannel>,
    ): CodeChannel? {
        for (sender in channels.mapNotNull { channel -> senders.firstOrNull { it.channel == channel } }) {
            val error = either<CodeDeliveryError, Unit> { sender.send(phone, code) }.leftOrNull() ?: return sender.channel
            if (error is CodeDeliveryError.Failed) {
                logger.warn("Код входа в кабинет через {} не отправлен: {}", sender.channel, error.reason)
                return null
            }
        }
        return null
    }

    private companion object {
        /** Статусы доступа, при которых организация платит за код. */
        val PAYABLE_STATUSES = setOf(ClientAppAccessStatus.INVITED, ClientAppAccessStatus.CONNECTED)
    }
}
