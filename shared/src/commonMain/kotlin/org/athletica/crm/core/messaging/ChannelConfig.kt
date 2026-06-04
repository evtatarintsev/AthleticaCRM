package org.athletica.crm.core.messaging

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Типизированный конфиг интеграции канала: провайдер-специфичные настройки (токены, отправитель и т.п.).
 *
 * Заменяет нетипизированный `Map<String, String>`: каждый провайдер описывает свои поля явно,
 * инвариант проверяется в момент десериализации (Parse, Don't Validate). Сериализуется полиморфно
 * с дискриминатором `type` и хранится в БД как JSONB. [provider] выводит провайдера (а через него —
 * [ChannelType]) из конкретного подтипа конфига, так что отдельные колонки-дискриминаторы не нужны.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface ChannelConfig {
    /** Провайдер, которому принадлежит этот конфиг. */
    val provider: ChannelProvider

    /** SMS через Twilio. */
    @Serializable
    @SerialName("twilio_sms")
    data class TwilioSms(
        val accountSid: String,
        val authToken: String,
        val from: String,
    ) : ChannelConfig {
        override val provider: ChannelProvider get() = ChannelProvider.TWILIO_SMS
    }

    /** SMS через SMSC. */
    @Serializable
    @SerialName("smsc_sms")
    data class SmscSms(
        val login: String,
        val password: String,
    ) : ChannelConfig {
        override val provider: ChannelProvider get() = ChannelProvider.SMSC_SMS
    }

    /** Telegram-бот. */
    @Serializable
    @SerialName("telegram_bot")
    data class TelegramBot(
        val botToken: String,
    ) : ChannelConfig {
        override val provider: ChannelProvider get() = ChannelProvider.TELEGRAM_BOT
    }

    /** Личный кабинет клиента: внешних настроек нет. */
    @Serializable
    @SerialName("in_app")
    data object InApp : ChannelConfig {
        override val provider: ChannelProvider get() = ChannelProvider.IN_APP
    }
}
