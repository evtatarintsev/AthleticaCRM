package org.athletica.crm.domain.clientapp.codes

import arrow.core.raise.context.Raise
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.domain.clientcontacts.PhoneDigits

/** Способ доставки кода входа в кабинет клиента. */
enum class CodeChannel { TELEGRAM, SMS }

/**
 * Порт доставки кода входа одним способом [channel]. Политика доставки (выбор канала,
 * переход на SMS) — в ядре кабинета; реализация лишь отправляет код за счёт подходящей
 * организации. Обращается к внешним сервисам, поэтому вызывается вне транзакции.
 */
interface VerificationCodeSender {
    /** Способ доставки, который реализует отправитель. */
    val channel: CodeChannel

    /** Отправляет [code] на [phone]; неудача — [CodeDeliveryError]. */
    context(raise: Raise<CodeDeliveryError>)
    suspend fun send(
        phone: PhoneDigits,
        code: VerificationCode,
    )
}

/** Неудача доставки кода. Не бизнес-ошибка: клиенту не показывается, пишется в лог. */
sealed interface CodeDeliveryError : AppError {
    /** Этим способом код на номер не доставить: нет организации-плательщика или у номера нет Telegram. */
    data object Undeliverable : CodeDeliveryError {
        override val code = "CODE_UNDELIVERABLE"
        override val message = "Код нельзя доставить этим способом"
    }

    /** Провайдер окончательно отказал в отправке. */
    data class Failed(
        /** Причина отказа для лога; без номера телефона и кода. */
        val reason: String,
    ) : CodeDeliveryError {
        override val code = "CODE_DELIVERY_FAILED"
        override val message = reason
    }
}
