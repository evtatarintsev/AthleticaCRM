package org.athletica.crm.api.schemas.channels

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.athletica.crm.core.entityids.ChannelIntegrationId

/**
 * Настроенная интеграция канала связи организации.
 * Секреты провайдера в ответ не попадают: [settings] содержит только их маски.
 */
@Serializable
data class ChannelIntegrationSchema(
    val id: ChannelIntegrationId,
    val name: String,
    /** Настройки провайдера без секретов; провайдер — подтип (поле `type`). */
    val settings: ChannelSettingsSchema,
    val enabled: Boolean,
    /** Чем поделиться с клиентами, чтобы они написали в канал; `null` — делиться нечем. */
    val share: ChannelShareSchema?,
)

/**
 * Настройки провайдера канала для показа: несекретные поля как есть, токены — маской
 * (`••••` и последние символы), пароли — не передаются вовсе.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface ChannelSettingsSchema {
    /** SMS через SMSC; пароль задан всегда и не показывается. */
    @Serializable
    @SerialName("smsc_sms")
    data class SmscSms(
        val login: String,
    ) : ChannelSettingsSchema

    /** Telegram-бот. */
    @Serializable
    @SerialName("telegram_bot")
    data class TelegramBot(
        /** Маска токена бота. */
        val tokenMask: String,
        /** Имя бота без `@`; пустая строка — интеграция ещё не проверялась. */
        val botUsername: String,
    ) : ChannelSettingsSchema

    /** SMS через Twilio. */
    @Serializable
    @SerialName("twilio_sms")
    data class TwilioSms(
        val accountSid: String,
        /** Маска auth token. */
        val authTokenMask: String,
        val from: String,
    ) : ChannelSettingsSchema
}

/** Данные для раздачи канала клиентам. */
@Serializable
data class ChannelShareSchema(
    /** Ссылка, по которой клиент попадает в канал (например, `https://t.me/<username>`). */
    val url: String,
)

/**
 * Настройки провайдера нового канала: все учётные данные обязательны.
 * Имя Telegram-бота не передаётся — сервер берёт его из Bot API.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface NewChannelConfig {
    /** SMS через SMSC. */
    @Serializable
    @SerialName("smsc_sms")
    data class SmscSms(
        val login: String,
        val password: String,
    ) : NewChannelConfig

    /** Telegram-бот. */
    @Serializable
    @SerialName("telegram_bot")
    data class TelegramBot(
        val botToken: String,
    ) : NewChannelConfig

    /** SMS через Twilio. */
    @Serializable
    @SerialName("twilio_sms")
    data class TwilioSms(
        val accountSid: String,
        val authToken: String,
        val from: String,
    ) : NewChannelConfig
}

/**
 * Изменение настроек провайдера существующего канала. Провайдер (`type`) должен совпадать с
 * сохранённым. Секрет `null` — оставить сохранённое значение.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface ChannelConfigChange {
    /** SMS через SMSC. */
    @Serializable
    @SerialName("smsc_sms")
    data class SmscSms(
        val login: String,
        /** Новый пароль; `null` — не менять. */
        val password: String?,
    ) : ChannelConfigChange

    /** Telegram-бот. */
    @Serializable
    @SerialName("telegram_bot")
    data class TelegramBot(
        /** Новый токен бота; `null` — не менять. */
        val botToken: String?,
    ) : ChannelConfigChange

    /** SMS через Twilio. */
    @Serializable
    @SerialName("twilio_sms")
    data class TwilioSms(
        val accountSid: String,
        /** Новый auth token; `null` — не менять. */
        val authToken: String?,
        val from: String,
    ) : ChannelConfigChange
}

/** Список интеграций каналов организации. */
@Serializable
data class ChannelListResponse(
    val channels: List<ChannelIntegrationSchema>,
)

/** Запрос на создание интеграции канала. Тип канала определяется провайдером [config]. */
@Serializable
data class CreateChannelIntegrationRequest(
    val id: ChannelIntegrationId,
    val name: String,
    val config: NewChannelConfig,
)

/** Запрос на изменение имени и настроек провайдера интеграции; включённость не меняет. */
@Serializable
data class UpdateChannelIntegrationRequest(
    val id: ChannelIntegrationId,
    val name: String,
    val config: ChannelConfigChange,
)

/** Запрос на включение или выключение интеграции канала. */
@Serializable
data class SetChannelEnabledRequest(
    val id: ChannelIntegrationId,
    val enabled: Boolean,
)

/** Запрос на удаление интеграции канала. */
@Serializable
data class DeleteChannelIntegrationRequest(
    val id: ChannelIntegrationId,
)
