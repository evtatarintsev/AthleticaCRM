package org.athletica.crm.core.messaging

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.athletica.crm.core.contacts.ContactType

/**
 * Типизированные настройки вида канала: провайдер-специфичные поля (токены, отправитель и т.п.)
 * плюс статические свойства вида (возможности, принимаемые типы контактов).
 *
 * Подтип конфига — и есть «вид канала»: отдельных дискриминаторов (тип канала/провайдер) больше нет.
 * Возможности ([supportsInbound], [supportsReceipts]) и [acceptedContactTypes] заданы на уровне подтипа,
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

    /** Типы контактов клиента, которые этот вид канала умеет адресовать. */
    val acceptedContactTypes: Set<ContactType>

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
        override val acceptedContactTypes: Set<ContactType> get() = setOf(ContactType.PHONE)
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
        override val acceptedContactTypes: Set<ContactType> get() = setOf(ContactType.PHONE)
    }

    /** Telegram-бот. */
    @Serializable
    @SerialName("telegram_bot")
    data class TelegramBot(
        val botToken: String,
    ) : ChannelConfig {
        override val supportsInbound: Boolean get() = true
        override val supportsReceipts: Boolean get() = true
        override val acceptedContactTypes: Set<ContactType> get() = setOf(ContactType.TELEGRAM)
    }
}
