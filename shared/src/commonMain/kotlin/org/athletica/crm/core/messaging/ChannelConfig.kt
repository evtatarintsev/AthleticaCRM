package org.athletica.crm.core.messaging

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.athletica.crm.core.contacts.ContactType

/**
 * Типизированные настройки вида канала: провайдер-специфичные поля (токены, отправитель и т.п.)
 * плюс статические свойства вида (возможности, способ адресации получателя).
 *
 * Подтип конфига — и есть «вид канала»: отдельных дискриминаторов (тип канала/провайдер) больше нет.
 * Возможности ([supportsInbound], [supportsReceipts]) и [addressing] заданы на уровне подтипа,
 * так как это свойства конкретной реализации, а не абстрактного типа. Сериализуется полиморфно с
 * дискриминатором `type` и хранится в БД как JSONB; при десериализации инвариант проверяется (Parse, Don't Validate).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface ChannelConfig {
    /** Умеет ли вид канала принимать входящие сообщения (webhook / long-poll). */
    val supportsInbound: Boolean

    /** Присылает ли вид канала квитанции о доставке/прочтении. */
    val supportsReceipts: Boolean

    /** Как вид канала находит адрес клиента: по контакту или по связке. */
    val addressing: ChannelAddressing

    /** SMS через Twilio. */
    @Serializable
    @SerialName("twilio_sms")
    data class TwilioSms(
        val accountSid: String,
        val authToken: String,
        val from: String,
    ) : ChannelConfig {
        override val supportsInbound: Boolean get() = false
        override val supportsReceipts: Boolean get() = true
        override val addressing: ChannelAddressing get() = ChannelAddressing.ByContact(setOf(ContactType.PHONE))
    }

    /** SMS через SMSC. */
    @Serializable
    @SerialName("smsc_sms")
    data class SmscSms(
        val login: String,
        val password: String,
    ) : ChannelConfig {
        override val supportsInbound: Boolean get() = false
        override val supportsReceipts: Boolean get() = true
        override val addressing: ChannelAddressing get() = ChannelAddressing.ByContact(setOf(ContactType.PHONE))
    }

    /** Telegram-бот. */
    @Serializable
    @SerialName("telegram_bot")
    data class TelegramBot(
        /** Токен бота из BotFather. */
        val botToken: String,
        /**
         * Имя пользователя бота без `@`. Клиент его не задаёт: при сохранении сервер подставляет
         * значение из Bot API (`getMe`). Пустая строка — интеграция ещё не проверялась.
         */
        val botUsername: String = "",
    ) : ChannelConfig {
        override val supportsInbound: Boolean get() = true
        override val supportsReceipts: Boolean get() = true
        override val addressing: ChannelAddressing get() = ChannelAddressing.ByBinding
    }
}
