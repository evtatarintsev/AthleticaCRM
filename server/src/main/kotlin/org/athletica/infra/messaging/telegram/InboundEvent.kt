package org.athletica.infra.messaging.telegram

import org.athletica.crm.core.Lang
import org.athletica.crm.i18n.BotMessages
import org.athletica.crm.i18n.LocalizationKey

/** Собеседник бота в личном чате. */
data class TelegramSender(
    /** Адрес чата — `chat.id` строкой. */
    val address: String,
    /** Имя и фамилия в Telegram. */
    val displayName: String,
    /** Имя пользователя без `@`; `null`, если не задано. */
    val username: String?,
)

/** Обновление Telegram, сведённое к тому, что нужно приёму входящих. */
sealed interface InboundEvent {
    /** Не обрабатывается: не сообщение или не личный чат. */
    data object Ignored : InboundEvent

    /** Команда `/start`: только служебный ответ, во входящие не пишется. */
    data class Start(val sender: TelegramSender) : InboundEvent

    /** Собеседник прислал свой контакт с номером [phone]. */
    data class OwnContact(val sender: TelegramSender, val phone: String) : InboundEvent

    /** Собеседник прислал чужой контакт — для привязки не годится. */
    data class ForeignContact(val sender: TelegramSender) : InboundEvent

    /** Сообщение с текстом [body] (для вложений — заглушка с видом и подписью). */
    data class Message(val sender: TelegramSender, val body: String) : InboundEvent
}

/** Сводит обновление к [InboundEvent]; заглушки вложений — на языке [lang]. */
fun TgUpdate.toInboundEvent(lang: Lang): InboundEvent {
    val message = message ?: return InboundEvent.Ignored
    val from = message.from
    if (message.chat.type != "private" || from == null) {
        return InboundEvent.Ignored
    }
    val sender =
        TelegramSender(
            address = message.chat.id.toString(),
            displayName = listOfNotNull(from.firstName, from.lastName).filter { it.isNotBlank() }.joinToString(" "),
            username = from.username,
        )
    val text = message.text
    val contact = message.contact
    return when {
        text != null && (text == "/start" || text.startsWith("/start ")) -> InboundEvent.Start(sender)
        text != null -> InboundEvent.Message(sender, text)
        contact != null && contact.userId == from.id -> InboundEvent.OwnContact(sender, contact.phoneNumber)
        contact != null -> InboundEvent.ForeignContact(sender)
        else -> InboundEvent.Message(sender, message.attachmentBody(lang))
    }
}

/** Заглушка вложения сообщения с подписью, если она есть. */
private fun TgMessage.attachmentBody(lang: Lang): String {
    val stub = BotMessages.Attachment.localize(lang, attachmentKind().localize(lang))
    val caption = caption?.takeIf { it.isNotBlank() } ?: return stub
    return "$stub $caption"
}

/** Вид вложения сообщения. */
private fun TgMessage.attachmentKind(): LocalizationKey =
    when {
        photo != null -> BotMessages.AttachmentPhoto
        video != null || videoNote != null -> BotMessages.AttachmentVideo
        voice != null -> BotMessages.AttachmentVoice
        audio != null -> BotMessages.AttachmentAudio
        document != null -> BotMessages.AttachmentDocument
        sticker != null -> BotMessages.AttachmentSticker
        location != null -> BotMessages.AttachmentLocation
        else -> BotMessages.AttachmentOther
    }
